package pt.vcc.parking.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import java.util.concurrent.TimeUnit
import pt.vcc.parking.MainActivity
import pt.vcc.parking.R
import pt.vcc.parking.domain.model.ParkedCar
import pt.vcc.parking.domain.model.ParkingReminder

/**
 * Canais, conteudo e accoes das notificacoes (`vp-11-reminders`).
 *
 * Sao dois canais e nao um: quem acha o lembrete periodico incomodo deve poder
 * desliga-lo sem perder o aviso do fim do prazo, que e a razao de ser da
 * funcionalidade.
 */
class ReminderNotifications(context: Context) {

    private val context = context.applicationContext
    private val manager = NotificationManagerCompat.from(this.context)

    /** `false` apaga qualquer tentativa de notificar: no Android 13+ e obrigatorio. */
    val enabled: Boolean
        get() = hasPostPermission(context) && manager.areNotificationsEnabled()

    /** Notificacao de baixa prioridade que acompanha o estacionamento a decorrer. */
    fun showOngoing(parkedCar: ParkedCar, reminder: ParkingReminder?, nowMillis: Long) {
        val remaining = reminder?.remainingMillis(nowMillis)
        val text = when {
            remaining == null -> context.getString(R.string.reminder_ongoing_no_deadline)
            remaining <= 0 -> context.getString(R.string.reminder_ongoing_expired)
            else -> context.getString(
                R.string.reminder_ongoing_remaining,
                remainingLabel(remaining),
            )
        }

        val builder = baseBuilder(CHANNEL_ACTIVE, NotificationCompat.PRIORITY_LOW)
            .setContentTitle(context.getString(R.string.reminder_ongoing_title))
            .setContentText(text)
            // `setWhen` com `setUsesChronometer` deixa o sistema contar o tempo
            // sozinho: sem isto seria preciso reemitir a notificacao a cada minuto.
            .setWhen(parkedCar.parkedAtMillis)
            .setUsesChronometer(true)
            .setOngoing(true)
            .addAction(returnAction())
            .addAction(endAction(parkedCar.id))

        if (reminder?.hasDeadline == true) builder.addAction(extendAction(parkedCar.id))

        notify(ONGOING_ID, builder.build())
    }

    /** Aviso antecipado, fim do prazo ou «ainda estacionado». */
    fun showAlarm(kind: ReminderAlarmKind, parkedCarId: Long, reminder: ParkingReminder?) {
        val warnBefore = reminder?.warnBeforeMillis ?: ParkingReminder.DEFAULT_WARN_BEFORE_MILLIS

        val builder = when (kind) {
            ReminderAlarmKind.Warning -> baseBuilder(CHANNEL_DEADLINE, NotificationCompat.PRIORITY_HIGH)
                .setContentTitle(context.getString(R.string.reminder_warning_title))
                .setContentText(
                    context.getString(
                        R.string.reminder_warning_message,
                        remainingLabel(warnBefore),
                    ),
                )
                .addAction(extendAction(parkedCarId))

            ReminderAlarmKind.Expired -> baseBuilder(CHANNEL_DEADLINE, NotificationCompat.PRIORITY_HIGH)
                .setContentTitle(context.getString(R.string.reminder_expired_title))
                .setContentText(context.getString(R.string.reminder_expired_message))
                .addAction(extendAction(parkedCarId))

            ReminderAlarmKind.StillParked -> baseBuilder(CHANNEL_ACTIVE, NotificationCompat.PRIORITY_DEFAULT)
                .setContentTitle(context.getString(R.string.reminder_still_parked_title))
                .setContentText(context.getString(R.string.reminder_still_parked_message))
        }

        builder
            .setAutoCancel(true)
            .addAction(returnAction())
            .addAction(endAction(parkedCarId))

        notify(kind.notificationId, builder.build())
    }

    /** Fim do estacionamento: nada do lembrete deve sobreviver na barra. */
    fun cancelAll() {
        manager.cancel(ONGOING_ID)
        ReminderAlarmKind.entries.forEach { manager.cancel(it.notificationId) }
    }

    fun cancelAlarm(kind: ReminderAlarmKind) = manager.cancel(kind.notificationId)

    /**
     * [enabled] ja confirmou a permissao, mas o lint nao consegue segui-lo ate
     * aqui; a permissao ainda pode ser revogada entre a verificacao e o envio, e
     * perder a notificacao e aceitavel, deitar a app abaixo nao e.
     */
    @SuppressLint("MissingPermission")
    private fun notify(id: Int, notification: Notification) {
        if (!enabled) return
        runCatching { manager.notify(id, notification) }
    }

    private fun baseBuilder(channelId: String, priority: Int): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_parking)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(priority)
            .setContentIntent(openAppIntent(openReturn = false))

    private fun returnAction(): NotificationCompat.Action = NotificationCompat.Action.Builder(
        0,
        context.getString(R.string.return_action_guide_me),
        openAppIntent(openReturn = true),
    ).build()

    private fun endAction(parkedCarId: Long): NotificationCompat.Action =
        NotificationCompat.Action.Builder(
            0,
            context.getString(R.string.parked_action_end),
            actionIntent(ReminderActionReceiver.ACTION_END, parkedCarId),
        ).build()

    private fun extendAction(parkedCarId: Long): NotificationCompat.Action =
        NotificationCompat.Action.Builder(
            0,
            context.getString(
                R.string.reminder_action_extend,
                TimeUnit.MILLISECONDS.toMinutes(ParkingReminder.EXTENSION_MILLIS).toInt(),
            ),
            actionIntent(ReminderActionReceiver.ACTION_EXTEND, parkedCarId),
        ).build()

    private fun openAppIntent(openReturn: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_RETURN, openReturn)

        return PendingIntent.getActivity(
            context,
            if (openReturn) REQUEST_OPEN_RETURN else REQUEST_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionIntent(action: String, parkedCarId: Long): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(action)
            // Tal como nos alarmes, o URI distingue os `PendingIntent`, que
            // ignoram extras na comparacao.
            .setData("vccparking://reminder-action/$parkedCarId/$action".toUri())
            .putExtra(ReminderActionReceiver.EXTRA_PARKED_CAR_ID, parkedCarId)

        return PendingIntent.getBroadcast(
            context,
            REQUEST_ACTION,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun remainingLabel(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis.coerceAtLeast(0L))
        val hours = minutes / MINUTES_PER_HOUR
        val rest = minutes % MINUTES_PER_HOUR

        return when {
            minutes < MINUTES_PER_HOUR -> context.getString(
                R.string.reminder_duration_minutes,
                minutes.toInt(),
            )

            rest == 0L -> context.getString(R.string.reminder_duration_hours, hours.toInt())
            else -> context.getString(
                R.string.reminder_duration_hours_minutes,
                hours.toInt(),
                rest.toInt(),
            )
        }
    }

    companion object {
        const val CHANNEL_ACTIVE = "parking_active"
        const val CHANNEL_DEADLINE = "parking_deadline"

        private const val ONGOING_ID = 2_001
        private const val REQUEST_OPEN_APP = 10
        private const val REQUEST_OPEN_RETURN = 11
        private const val REQUEST_ACTION = 12
        private const val MINUTES_PER_HOUR = 60L

        /**
         * Ids distintos por tipo: um aviso antecipado nao deve apagar da barra a
         * notificacao de prazo terminado, nem o contrario.
         */
        private val ReminderAlarmKind.notificationId: Int
            get() = 2_100 + ordinal

        /**
         * Criados no arranque e nao no primeiro envio: no Android 8+ um canal
         * inexistente faz a notificacao ser descartada em silencio.
         */
        fun createChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

            val manager = context.getSystemService<NotificationManager>() ?: return

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ACTIVE,
                    context.getString(R.string.reminder_channel_active),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.reminder_channel_active_description)
                    setShowBadge(false)
                },
            )

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_DEADLINE,
                    context.getString(R.string.reminder_channel_deadline),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.reminder_channel_deadline_description)
                },
            )
        }

        /** Antes do Android 13 nao ha permissao a pedir: considera-se concedida. */
        fun hasPostPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
    }
}
