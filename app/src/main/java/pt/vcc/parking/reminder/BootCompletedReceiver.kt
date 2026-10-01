package pt.vcc.parking.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.launch
import pt.vcc.parking.VccParkingApplication

/**
 * Reagenda os lembretes apos um reinicio (`vp-11-reminders`).
 *
 * Os alarmes do `AlarmManager` nao sobrevivem a um arranque. Sem este recetor o
 * lembrete desapareceria sem aviso nenhum, que e o pior resultado possivel para
 * uma funcionalidade cuja unica promessa e avisar.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val application = context.applicationContext as? VccParkingApplication ?: return
        val pending = goAsync()

        application.applicationScope.launch {
            try {
                application.reminderRepository.pending().forEach { reminder ->
                    application.reminderScheduler.schedule(reminder)
                }
            } catch (error: Exception) {
                Log.w(TAG, "Falha a reagendar lembretes: ${error.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootCompletedReceiver"
    }
}
