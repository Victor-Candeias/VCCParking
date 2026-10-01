package pt.vcc.parking.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.launch
import pt.vcc.parking.VccParkingApplication
import pt.vcc.parking.domain.model.ParkedCar

/**
 * Accoes das notificacoes (`vp-11-reminders`).
 *
 * «Ja sai» e «Mais 30 min» resolvem-se sem abrir a app: obrigar a abrir o ecra
 * para carregar num botao que ja esta visivel na barra seria trabalho a mais
 * para quem esta a chegar ao carro.
 */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_END && action != ACTION_EXTEND) return

        val parkedCarId = intent.getLongExtra(EXTRA_PARKED_CAR_ID, ParkedCar.NO_ID)
        if (parkedCarId == ParkedCar.NO_ID) return

        val application = context.applicationContext as? VccParkingApplication ?: return
        val pending = goAsync()

        application.applicationScope.launch {
            try {
                when (action) {
                    ACTION_END -> endParking(application, parkedCarId)
                    ACTION_EXTEND -> extendReminder(application, parkedCarId)
                }
            } catch (error: Exception) {
                Log.w(TAG, "Falha a tratar a accao ${action}: ${error.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * O coordenador reage ao fim do estacionamento e limpa tudo, mas a barra e
     * limpa aqui tambem: entre o toque e a emissao da base de dados havia uma
     * notificacao que ja nao corresponde a nada.
     */
    private suspend fun endParking(application: VccParkingApplication, parkedCarId: Long) {
        application.reminderNotifications.cancelAll()
        application.reminderScheduler.cancel(parkedCarId)
        application.reminderRepository.clear(parkedCarId)
        application.parkedCarRepository.endActive()
    }

    private suspend fun extendReminder(application: VccParkingApplication, parkedCarId: Long) {
        val extended = application.reminderRepository.extend(parkedCarId) ?: return
        application.reminderScheduler.schedule(extended)
        // O aviso respondido deixa de fazer sentido; o persistente fica.
        application.reminderNotifications.cancelAlarm(ReminderAlarmKind.Warning)
        application.reminderNotifications.cancelAlarm(ReminderAlarmKind.Expired)
    }

    companion object {
        const val ACTION_END = "pt.vcc.parking.action.REMINDER_END"
        const val ACTION_EXTEND = "pt.vcc.parking.action.REMINDER_EXTEND"
        const val EXTRA_PARKED_CAR_ID = "parkedCarId"

        private const val TAG = "ReminderActionReceiver"
    }
}
