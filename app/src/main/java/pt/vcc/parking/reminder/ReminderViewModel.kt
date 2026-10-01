package pt.vcc.parking.reminder

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.vcc.parking.VccParkingApplication
import pt.vcc.parking.data.repository.ParkedCarRepository
import pt.vcc.parking.data.repository.ReminderRepository
import pt.vcc.parking.domain.model.ParkingReminder

/**
 * Definicao do lembrete do estacionamento ativo (`vp-11-reminders`).
 *
 * So escreve na base de dados: quem agenda e cancela alarmes e o
 * [ReminderCoordinator], que reage ao estado guardado. Assim a mesma alteracao
 * feita a partir da UI ou de uma accao da notificacao produz exatamente o mesmo
 * resultado.
 */
class ReminderViewModel(
    private val repository: ReminderRepository,
    private val parkedCars: ParkedCarRepository,
    private val scheduler: ReminderScheduler,
    private val notifications: ReminderNotifications,
) : ViewModel() {

    private val capabilities = MutableStateFlow(readCapabilities())

    val uiState: StateFlow<ReminderUiState> = combine(
        parkedCars.observeActive(),
        repository.observeActive(),
        capabilities,
    ) { parkedCar, reminder, capabilities ->
        ReminderUiState(
            parkedCarId = parkedCar?.id,
            reminder = reminder,
            notificationsEnabled = capabilities.notificationsEnabled,
            canScheduleExact = capabilities.canScheduleExact,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = ReminderUiState(),
    )

    /**
     * As permissoes podem mudar nas definicoes do sistema, fora da app; sem
     * esta releitura o ecra continuaria a mostrar um aviso ja resolvido.
     */
    fun refreshCapabilities() {
        capabilities.value = readCapabilities()
    }

    /** [durationMillis] a `null` guarda o lembrete periodico sem prazo. */
    fun setReminder(
        durationMillis: Long?,
        warnBeforeMillis: Long = ParkingReminder.DEFAULT_WARN_BEFORE_MILLIS,
        recurringEveryMillis: Long? = null,
    ) {
        val parkedCarId = uiState.value.parkedCarId ?: return

        viewModelScope.launch {
            runCatchingPersistence {
                repository.setDeadline(
                    parkedCarId = parkedCarId,
                    durationMillis = durationMillis,
                    warnBeforeMillis = warnBeforeMillis,
                    recurringEveryMillis = recurringEveryMillis,
                )
            }
        }
    }

    /** «Mais 30 min», tambem disponivel no cartao e nao so na notificacao. */
    fun extend(extraMillis: Long = ParkingReminder.EXTENSION_MILLIS) {
        val parkedCarId = uiState.value.parkedCarId ?: return

        viewModelScope.launch {
            runCatchingPersistence { repository.extend(parkedCarId, extraMillis) }
        }
    }

    fun clear() {
        val parkedCarId = uiState.value.parkedCarId ?: return

        viewModelScope.launch {
            runCatchingPersistence {
                repository.clear(parkedCarId)
                scheduler.cancel(parkedCarId)
            }
        }
    }

    private fun readCapabilities() = Capabilities(
        notificationsEnabled = notifications.enabled,
        canScheduleExact = scheduler.canScheduleExact,
    )

    /** Mesma regra de `vp-08-park-save`: falhar a escrita nao derruba a app. */
    private inline fun <T> runCatchingPersistence(block: () -> T): Boolean = try {
        block()
        true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Log.w(TAG, "Falha a gravar o lembrete: ${error.javaClass.simpleName}")
        false
    }

    private data class Capabilities(
        val notificationsEnabled: Boolean,
        val canScheduleExact: Boolean,
    )

    companion object {
        private const val TAG = "ReminderViewModel"
        private const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY],
                ) as VccParkingApplication

                ReminderViewModel(
                    repository = application.reminderRepository,
                    parkedCars = application.parkedCarRepository,
                    scheduler = application.reminderScheduler,
                    notifications = application.reminderNotifications,
                )
            }
        }
    }
}
