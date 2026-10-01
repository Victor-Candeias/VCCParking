package pt.vcc.parking.parked

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pt.vcc.parking.VccParkingApplication
import pt.vcc.parking.data.repository.ParkedCarRepository
import pt.vcc.parking.domain.model.Parking
import pt.vcc.parking.location.FusedLocationProvider
import pt.vcc.parking.location.LocationProvider
import pt.vcc.parking.location.LocationResult

/**
 * Captura, edicao e termino do estacionamento (`vp-08-park-save`).
 *
 * O estado apresentado nasce de duas fontes: o registo ativo, que vive na base
 * de dados, e a captura em curso, que e transitoria. Mante-las separadas evita
 * que uma captura falhada apague do ecra um estacionamento que continua valido.
 */
class ParkedCarViewModel(
    private val repository: ParkedCarRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val capture = MutableStateFlow<Capture>(Capture.Idle)
    private var captureJob: Job? = null

    val uiState: StateFlow<ParkedCarUiState> =
        combine(repository.observeActive(), capture) { active, capture ->
            when (capture) {
                Capture.Idle -> active?.let(ParkedCarUiState::Active) ?: ParkedCarUiState.Empty
                Capture.Running -> ParkedCarUiState.Capturing
                is Capture.Failed -> ParkedCarUiState.CaptureFailed(capture.reason, active)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = ParkedCarUiState.Empty,
        )

    /**
     * «Estacionei aqui»: guarda a posicao atual.
     *
     * Quando a captura falha mas o registo nasceu do detalhe de um parque, a
     * posicao do parque e melhor do que nenhuma — e exatamente onde o utilizador
     * disse que estava. So sem esse ponto de partida e que se pede a marcacao
     * manual no mapa.
     */
    fun parkHere(parking: Parking? = null) {
        if (capture.value == Capture.Running) return

        captureJob?.cancel()
        capture.value = Capture.Running

        captureJob = viewModelScope.launch {
            val result = withTimeoutOrNull(CAPTURE_TIMEOUT_MILLIS) {
                locationProvider.currentLocation()
            }

            when (result) {
                is LocationResult.Available -> save(
                    latitude = result.location.latitude,
                    longitude = result.location.longitude,
                    accuracyMeters = result.location.accuracyMeters,
                    parking = parking,
                )

                else -> if (parking != null) {
                    save(
                        latitude = parking.latitude,
                        longitude = parking.longitude,
                        accuracyMeters = null,
                        parking = parking,
                    )
                } else {
                    capture.value = Capture.Failed(result.toCaptureError())
                }
            }
        }
    }

    /** Ponto marcado a mao no mapa; sem precisao medida porque nao houve medicao. */
    fun parkAt(latitude: Double, longitude: Double, parking: Parking? = null) {
        captureJob?.cancel()
        captureJob = viewModelScope.launch {
            save(latitude = latitude, longitude = longitude, accuracyMeters = null, parking = parking)
        }
    }

    fun updateDetails(id: Long, note: String?, photoUri: String?) {
        viewModelScope.launch {
            runCatchingPersistence { repository.updateDetails(id, note, photoUri) }
        }
    }

    /** Correcao manual da posicao de um registo que ja existe. */
    fun moveTo(id: Long, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            runCatchingPersistence { repository.updatePosition(id, latitude, longitude) }
        }
    }

    /** O registo nao e apagado: passa a pertencer ao historico de `vp-10-history`. */
    fun endActive() {
        viewModelScope.launch {
            runCatchingPersistence { repository.endActive() }
        }
    }

    /** Fecha a mensagem de erro e devolve o ecra ao estado real. */
    fun dismissCaptureError() {
        if (capture.value is Capture.Failed) capture.value = Capture.Idle
    }

    private suspend fun save(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float?,
        parking: Parking?,
    ) {
        val saved = runCatchingPersistence {
            repository.park(
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = accuracyMeters,
                parking = parking,
            )
        }
        capture.value = if (saved) Capture.Idle else Capture.Failed(ParkedCarCaptureError.Unavailable)
    }

    /**
     * Uma falha de escrita nao pode derrubar a app, mas o cancelamento tem de
     * continuar a propagar — caso contrario o `viewModelScope` deixava de poder
     * ser cancelado de forma limpa.
     */
    private inline fun <T> runCatchingPersistence(block: () -> T): Boolean = try {
        block()
        true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Log.w(TAG, "Falha a gravar o estacionamento: ${error.javaClass.simpleName}")
        false
    }

    private fun LocationResult?.toCaptureError(): ParkedCarCaptureError = when (this) {
        LocationResult.PermissionMissing -> ParkedCarCaptureError.PermissionMissing
        LocationResult.LocationDisabled -> ParkedCarCaptureError.LocationDisabled
        else -> ParkedCarCaptureError.Unavailable
    }

    private sealed interface Capture {
        data object Idle : Capture

        data object Running : Capture

        data class Failed(val reason: ParkedCarCaptureError) : Capture
    }

    companion object {
        private const val TAG = "ParkedCarViewModel"

        /**
         * Num parque subterraneo o GPS nunca chega. Esperar os 20 s do
         * `FusedLocationProvider` sem resposta nenhuma parece uma avaria; ao fim
         * de 10 s e mais honesto oferecer a marcacao manual.
         */
        const val CAPTURE_TIMEOUT_MILLIS = 10_000L

        /** Sobrevive a rotacao sem voltar a subscrever a base de dados. */
        private const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY],
                ) as VccParkingApplication

                ParkedCarViewModel(
                    repository = application.parkedCarRepository,
                    locationProvider = FusedLocationProvider(application),
                )
            }
        }
    }
}
