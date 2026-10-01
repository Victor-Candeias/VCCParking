package pt.vcc.parking.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.vcc.parking.VccParkingApplication
import pt.vcc.parking.data.local.HistoryRetention
import pt.vcc.parking.data.repository.ParkingHistoryRepository

/** Escrita do ficheiro exportado; o destino vem do seletor do sistema. */
fun interface HistoryExportSink {
    suspend fun write(content: String)
}

/**
 * Histórico de estacionamentos (`vp-10-history`).
 *
 * A lista vem da base de dados e o resto — mensagens e exportacao em curso — e
 * transitorio. Mante-los separados evita que uma `Snackbar` por fechar impeca a
 * lista de refletir o que esta mesmo guardado.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val repository: ParkingHistoryRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val limit = MutableStateFlow(PAGE_SIZE)
    private val transient = MutableStateFlow(Transient())

    val uiState: StateFlow<HistoryUiState> = combine(
        limit.flatMapLatest(repository::observeHistory),
        repository.observeCount(),
        repository.retention,
        transient,
    ) { entries, totalCount, retention, transient ->
        HistoryUiState(
            entries = entries,
            totalCount = totalCount,
            retention = retention,
            loading = false,
            exporting = transient.exporting,
            message = transient.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = HistoryUiState(),
    )

    /**
     * A retencao e aplicada ao abrir o ecra e nao por um trabalho periodico:
     * o historico so interessa a quem o esta a ver.
     */
    init {
        viewModelScope.launch { runCatchingPersistence { repository.applyRetention() } }
    }

    fun loadMore() {
        if (uiState.value.canLoadMore) limit.value += PAGE_SIZE
    }

    /**
     * Apaga um registo mas guarda-o para anular: a accao e destrutiva e um
     * toque errado numa lista nao devia custar informacao.
     */
    fun delete(id: Long) {
        viewModelScope.launch {
            val removed = try {
                repository.delete(id)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.w(TAG, "Falha a apagar o registo: ${error.javaClass.simpleName}")
                null
            }

            if (removed != null) publish(HistoryMessage.Deleted(removed))
        }
    }

    fun undoDelete() {
        val deleted = (transient.value.message as? HistoryMessage.Deleted)?.entry ?: return
        transient.value = transient.value.copy(message = null)
        viewModelScope.launch { runCatchingPersistence { repository.restore(deleted) } }
    }

    /** Fechar a mensagem de remocao e o que a torna definitiva para a fotografia. */
    fun dismissMessage() {
        val message = transient.value.message ?: return
        transient.value = transient.value.copy(message = null)
        finalize(message)
    }

    fun deleteAll() {
        viewModelScope.launch {
            val removed = try {
                repository.deleteAll()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.w(TAG, "Falha a apagar o historico: ${error.javaClass.simpleName}")
                return@launch
            }

            publish(HistoryMessage.Cleared(removed))
        }
    }

    fun setRetention(retention: HistoryRetention) {
        viewModelScope.launch { runCatchingPersistence { repository.setRetention(retention) } }
    }

    fun suggestedFileName(format: HistoryExportFormat): String =
        HistoryExporter.fileName(format, now())

    /**
     * Exporta o historico guardado no momento — nunca a pagina visivel — para
     * que o ficheiro nao dependa de ate onde o utilizador rolou a lista.
     */
    fun export(format: HistoryExportFormat, sink: HistoryExportSink) {
        if (transient.value.exporting) return
        transient.value = transient.value.copy(exporting = true)

        viewModelScope.launch {
            val exported = try {
                sink.write(HistoryExporter.export(repository.history(), format))
                true
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.w(TAG, "Falha a exportar o historico: ${error.javaClass.simpleName}")
                false
            }

            transient.value.message?.let(::finalize)
            transient.value = Transient(
                exporting = false,
                message = if (exported) HistoryMessage.Exported else HistoryMessage.ExportFailed,
            )
        }
    }

    /** Uma mensagem nova substitui a anterior, que deixa de poder ser anulada. */
    private fun publish(message: HistoryMessage) {
        transient.value.message?.let(::finalize)
        transient.value = transient.value.copy(message = message)
    }

    /**
     * A fotografia so e apagada quando a remocao deixa de poder ser anulada:
     * restaurar um registo sem a imagem seria restaurar outra coisa.
     */
    private fun finalize(message: HistoryMessage) {
        val entry = (message as? HistoryMessage.Deleted)?.entry ?: return
        viewModelScope.launch { runCatchingPersistence { repository.discardPhoto(entry) } }
    }

    private inline fun <T> runCatchingPersistence(block: () -> T): Boolean = try {
        block()
        true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Log.w(TAG, "Falha no historico: ${error.javaClass.simpleName}")
        false
    }

    private data class Transient(
        val exporting: Boolean = false,
        val message: HistoryMessage? = null,
    )

    companion object {
        private const val TAG = "HistoryViewModel"

        /**
         * Chega para encher o ecra mais alto sem ler centenas de linhas de uma
         * vez; o resto so e lido se o utilizador continuar a rolar.
         */
        const val PAGE_SIZE = 30

        private const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY],
                ) as VccParkingApplication

                HistoryViewModel(application.parkingHistoryRepository)
            }
        }
    }
}
