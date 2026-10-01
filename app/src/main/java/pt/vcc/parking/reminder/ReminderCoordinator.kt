package pt.vcc.parking.reminder

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import pt.vcc.parking.data.repository.ParkedCarRepository
import pt.vcc.parking.data.repository.ReminderRepository
import pt.vcc.parking.domain.model.ParkedCar
import pt.vcc.parking.domain.model.ParkingReminder

/**
 * Mantem alarmes e notificacoes alinhados com o que esta na base de dados
 * (`vp-11-reminders`).
 *
 * Existe para que «terminar o estacionamento cancela os alarmes» seja uma
 * consequencia do estado e nao uma chamada que alguem se pode esquecer de
 * fazer: a UI, as accoes da notificacao e o arranque escrevem todos na mesma
 * tabela, e e esta classe que reage.
 */
class ReminderCoordinator(
    private val parkedCars: ParkedCarRepository,
    private val reminders: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val notifications: ReminderNotifications,
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    /** Ultimo carro sincronizado; serve para cancelar os alarmes certos ao terminar. */
    private var lastParkedCarId: Long? = null

    fun start(scope: CoroutineScope) {
        scope.launch {
            // Reagendamento de arranque: o processo pode ter sido morto com
            // alarmes por disparar.
            runCatching { reminders.pending().forEach(scheduler::schedule) }
                .onFailure { Log.w(TAG, "Falha no reagendamento inicial: ${it.javaClass.simpleName}") }

            combine(
                parkedCars.observeActive(),
                reminders.observeActive(),
            ) { parkedCar, reminder -> parkedCar to reminder }
                .distinctUntilChanged()
                .collect { (parkedCar, reminder) -> sync(parkedCar, reminder) }
        }
    }

    private fun sync(parkedCar: ParkedCar?, reminder: ParkingReminder?) {
        if (parkedCar == null) {
            lastParkedCarId?.let(scheduler::cancel)
            notifications.cancelAll()
            lastParkedCarId = null
            return
        }

        // Trocar de carro sem terminar o anterior nao acontece pela UI, mas um
        // alarme orfao de um registo antigo continuaria a tocar.
        lastParkedCarId?.takeIf { it != parkedCar.id }?.let(scheduler::cancel)
        lastParkedCarId = parkedCar.id

        if (reminder == null || reminder.isSilent) {
            scheduler.cancel(parkedCar.id)
        } else {
            scheduler.schedule(reminder)
        }

        notifications.showOngoing(parkedCar, reminder, now())
    }

    private companion object {
        const val TAG = "ReminderCoordinator"
    }
}
