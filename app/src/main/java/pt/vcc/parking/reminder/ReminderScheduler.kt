package pt.vcc.parking.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import pt.vcc.parking.domain.model.ParkingReminder

/**
 * Agendamento dos lembretes (`vp-11-reminders`).
 *
 * E uma interface para o resto da app poder ser testado sem `AlarmManager`: o
 * que interessa verificar e que terminar o estacionamento cancela e que um
 * prazo novo reagenda, nao a chamada ao sistema em si.
 */
interface ReminderScheduler {

    /**
     * `false` quando o Android 12+ nao autoriza alarmes exatos.
     *
     * A app continua a agendar, mas com uma janela inexata, e a UI avisa — um
     * aviso de parquimetro que pode chegar atrasado ainda vale mais do que
     * nenhum, desde que o utilizador saiba disso.
     */
    val canScheduleExact: Boolean

    fun schedule(reminder: ParkingReminder)

    fun cancel(parkedCarId: Long)
}

/** Implementacao sobre o `AlarmManager`. */
class AlarmReminderScheduler(
    context: Context,
    private val now: () -> Long = { System.currentTimeMillis() },
) : ReminderScheduler {

    private val context = context.applicationContext
    private val alarmManager = requireNotNull(this.context.getSystemService<AlarmManager>()) {
        "AlarmManager indisponivel"
    }

    override val canScheduleExact: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    override fun schedule(reminder: ParkingReminder) {
        cancel(reminder.parkedCarId)

        ReminderPlan.alarmsFor(reminder, now()).forEach { alarm ->
            val intent = pendingIntent(alarm.parkedCarId, alarm.kind)
            if (canScheduleExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarm.triggerAtMillis,
                    intent,
                )
            } else {
                // Sem alarme exato o sistema agrupa o disparo com outros; o
                // `AndIdle` continua a garantir que chega mesmo em Doze.
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarm.triggerAtMillis,
                    intent,
                )
            }
        }
    }

    override fun cancel(parkedCarId: Long) {
        ReminderAlarmKind.entries.forEach { kind ->
            alarmManager.cancel(pendingIntent(parkedCarId, kind))
        }
    }

    /**
     * O par carro/tipo entra no `data` e nao nos extras.
     *
     * A igualdade de `PendingIntent` ignora extras: sem o URI distinto, o
     * segundo alarme substituiria o primeiro e so uma das notificacoes
     * chegaria.
     */
    private fun pendingIntent(parkedCarId: Long, kind: ReminderAlarmKind): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FIRE)
            .setData(alarmUri(parkedCarId, kind))
            .putExtra(ReminderReceiver.EXTRA_PARKED_CAR_ID, parkedCarId)
            .putExtra(ReminderReceiver.EXTRA_KIND, kind.name)

        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val REQUEST_CODE = 0

        fun alarmUri(parkedCarId: Long, kind: ReminderAlarmKind): Uri =
            "vccparking://reminder/$parkedCarId/${kind.name}".toUri()
    }
}
