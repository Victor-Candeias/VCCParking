package pt.vcc.parking.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.launch
import pt.vcc.parking.VccParkingApplication
import pt.vcc.parking.domain.model.ParkedCar

/**
 * Recebe o alarme e transforma-o numa notificacao (`vp-11-reminders`).
 *
 * O trabalho e assincrono porque ler a base de dados num `onReceive` bloquearia
 * a thread principal; o [goAsync] mantem o processo vivo ate a notificacao
 * estar enviada.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return

        val parkedCarId = intent.getLongExtra(EXTRA_PARKED_CAR_ID, ParkedCar.NO_ID)
        val kind = intent.getStringExtra(EXTRA_KIND)?.let { name ->
            ReminderAlarmKind.entries.firstOrNull { it.name == name }
        }
        if (parkedCarId == ParkedCar.NO_ID || kind == null) return

        val application = context.applicationContext as? VccParkingApplication ?: return
        val pending = goAsync()

        application.applicationScope.launch {
            try {
                val active = application.parkedCarRepository.active()

                // Um alarme de um estacionamento ja terminado nao avisa ninguem:
                // limpa-se o que ficou para tras e sai-se em silencio.
                if (active == null || active.id != parkedCarId) {
                    application.reminderScheduler.cancel(parkedCarId)
                    return@launch
                }

                val reminder = application.reminderRepository.byParkedCarId(parkedCarId)
                application.reminderNotifications.showAlarm(kind, parkedCarId, reminder)

                // O periodico e agendado um de cada vez; e aqui que o seguinte nasce.
                if (kind == ReminderAlarmKind.StillParked && reminder != null) {
                    application.reminderScheduler.schedule(reminder)
                }
            } catch (error: Exception) {
                Log.w(TAG, "Falha a tratar o alarme: ${error.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "pt.vcc.parking.action.REMINDER_FIRE"
        const val EXTRA_PARKED_CAR_ID = "parkedCarId"
        const val EXTRA_KIND = "kind"

        private const val TAG = "ReminderReceiver"
    }
}
