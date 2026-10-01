package pt.vcc.parking.reminder

import pt.vcc.parking.domain.model.ParkingReminder

/** Os tres momentos em que um lembrete fala com o utilizador (`vp-11-reminders`). */
enum class ReminderAlarmKind {
    /** «Faltam 15 minutos»; antecedencia escolhida pelo utilizador. */
    Warning,

    /** «O tempo terminou». */
    Expired,

    /** «O carro continua estacionado»; opcional e repetido. */
    StillParked,
}

/** Um alarme concreto a entregar ao sistema. */
data class ReminderAlarm(
    val parkedCarId: Long,
    val kind: ReminderAlarmKind,
    val triggerAtMillis: Long,
)

/**
 * Decide que alarmes um lembrete precisa, sem tocar no Android.
 *
 * Separar isto do agendador e o que permite testar as regras em JVM: o
 * `AlarmManager` nao tem como ser observado num teste unitario, mas a decisao
 * de «que alarmes e a que horas» e exatamente onde os enganos acontecem.
 */
object ReminderPlan {

    /**
     * Alarmes ainda por disparar para [reminder], a partir de [nowMillis].
     *
     * Instantes ja passados nao entram: agendar para tras faria o `AlarmManager`
     * disparar de imediato e o utilizador receberia um aviso de um prazo que ja
     * tinha acabado quando abriu a app.
     */
    fun alarmsFor(reminder: ParkingReminder, nowMillis: Long): List<ReminderAlarm> {
        if (reminder.dismissed) return emptyList()

        val alarms = mutableListOf<ReminderAlarm>()

        val warnAt = reminder.warnAtMillis
        // Com antecedencia zero o aviso coincidiria com o fim do prazo e o
        // utilizador receberia duas notificacoes seguidas a dizer o mesmo.
        if (warnAt != null && reminder.warnBeforeMillis > 0 && warnAt > nowMillis) {
            alarms += ReminderAlarm(reminder.parkedCarId, ReminderAlarmKind.Warning, warnAt)
        }

        reminder.expiresAtMillis?.let { expiresAt ->
            if (expiresAt > nowMillis) {
                alarms += ReminderAlarm(reminder.parkedCarId, ReminderAlarmKind.Expired, expiresAt)
            }
        }

        nextStillParkedAt(reminder, nowMillis)?.let { triggerAt ->
            alarms += ReminderAlarm(reminder.parkedCarId, ReminderAlarmKind.StillParked, triggerAt)
        }

        return alarms
    }

    /**
     * Proximo «ainda estacionado», ou `null` se o lembrete periodico esta desligado.
     *
     * So e agendado um de cada vez: o receptor volta a chamar isto quando
     * dispara, o que evita um alarme repetido a sobreviver ao fim do
     * estacionamento.
     */
    fun nextStillParkedAt(reminder: ParkingReminder, nowMillis: Long): Long? {
        val period = reminder.recurringEveryMillis ?: return null
        if (period <= 0) return null
        return nowMillis + period
    }
}
