package pt.vcc.parking.reminder

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.vcc.parking.domain.model.ParkingReminder

/**
 * Regras de agendamento de `vp-11-reminders`.
 *
 * Testa o [ReminderPlan] e nao o `AlarmManager`: o que pode correr mal aqui sao
 * as horas e os alarmes em falta, nao a chamada ao sistema.
 */
class ReminderSchedulerTest {

    @Test
    fun `deadline schedules the warning and the expiry`() {
        val reminder = ParkingReminder(
            parkedCarId = PARKED_CAR_ID,
            expiresAtMillis = NOW + TWO_HOURS,
            warnBeforeMillis = FIFTEEN_MINUTES,
        )

        val alarms = ReminderPlan.alarmsFor(reminder, NOW)

        assertEquals(
            listOf(ReminderAlarmKind.Warning, ReminderAlarmKind.Expired),
            alarms.map { it.kind },
        )
        assertEquals(NOW + TWO_HOURS - FIFTEEN_MINUTES, alarms.first().triggerAtMillis)
        assertEquals(NOW + TWO_HOURS, alarms.last().triggerAtMillis)
    }

    @Test
    fun `warning in the past is not scheduled but the expiry still is`() {
        val reminder = ParkingReminder(
            parkedCarId = PARKED_CAR_ID,
            // Faltam cinco minutos e o aviso era para quinze: ja passou.
            expiresAtMillis = NOW + FIVE_MINUTES,
            warnBeforeMillis = FIFTEEN_MINUTES,
        )

        val alarms = ReminderPlan.alarmsFor(reminder, NOW)

        assertEquals(listOf(ReminderAlarmKind.Expired), alarms.map { it.kind })
    }

    @Test
    fun `zero warning does not duplicate the expiry alarm`() {
        val reminder = ParkingReminder(
            parkedCarId = PARKED_CAR_ID,
            expiresAtMillis = NOW + TWO_HOURS,
            warnBeforeMillis = 0L,
        )

        val alarms = ReminderPlan.alarmsFor(reminder, NOW)

        assertEquals(listOf(ReminderAlarmKind.Expired), alarms.map { it.kind })
    }

    @Test
    fun `expired deadline schedules nothing`() {
        val reminder = ParkingReminder(
            parkedCarId = PARKED_CAR_ID,
            expiresAtMillis = NOW - FIVE_MINUTES,
        )

        assertTrue(ReminderPlan.alarmsFor(reminder, NOW).isEmpty())
    }

    @Test
    fun `dismissed reminder schedules nothing`() {
        val reminder = ParkingReminder(
            parkedCarId = PARKED_CAR_ID,
            expiresAtMillis = NOW + TWO_HOURS,
            recurringEveryMillis = TWO_HOURS,
            dismissed = true,
        )

        assertTrue(ReminderPlan.alarmsFor(reminder, NOW).isEmpty())
    }

    @Test
    fun `recurring reminder works without a deadline`() {
        val reminder = ParkingReminder(
            parkedCarId = PARKED_CAR_ID,
            recurringEveryMillis = TWO_HOURS,
        )

        val alarms = ReminderPlan.alarmsFor(reminder, NOW)

        assertEquals(listOf(ReminderAlarmKind.StillParked), alarms.map { it.kind })
        assertEquals(NOW + TWO_HOURS, alarms.single().triggerAtMillis)
    }

    @Test
    fun `recurring reminder is scheduled one at a time`() {
        val reminder = ParkingReminder(
            parkedCarId = PARKED_CAR_ID,
            recurringEveryMillis = TWO_HOURS,
        )

        val first = ReminderPlan.nextStillParkedAt(reminder, NOW)
        val second = ReminderPlan.nextStillParkedAt(reminder, NOW + TWO_HOURS)

        assertEquals(NOW + TWO_HOURS, first)
        assertEquals(NOW + TWO_HOURS + TWO_HOURS, second)
    }

    @Test
    fun `recurring reminder off has no next occurrence`() {
        val reminder = ParkingReminder(parkedCarId = PARKED_CAR_ID, expiresAtMillis = NOW + TWO_HOURS)

        assertNull(ReminderPlan.nextStillParkedAt(reminder, NOW))
    }

    @Test
    fun `reminder without deadline nor repetition is silent`() {
        assertTrue(ParkingReminder(parkedCarId = PARKED_CAR_ID).isSilent)
    }

    @Test
    fun `extending moves the deadline and brings the warning back`() {
        val reminder = ParkingReminder(
            parkedCarId = PARKED_CAR_ID,
            expiresAtMillis = NOW + FIVE_MINUTES,
            dismissed = true,
        )

        val extended = reminder.extendedBy(ParkingReminder.EXTENSION_MILLIS, NOW)

        assertEquals(NOW + FIVE_MINUTES + ParkingReminder.EXTENSION_MILLIS, extended.expiresAtMillis)
        assertTrue(ReminderPlan.alarmsFor(extended, NOW).isNotEmpty())
    }

    @Test
    fun `extending an overdue deadline counts from now`() {
        val reminder = ParkingReminder(
            parkedCarId = PARKED_CAR_ID,
            // Uma hora de atraso: somar ao prazo antigo daria um prazo novo que
            // ja nascia expirado.
            expiresAtMillis = NOW - ONE_HOUR,
        )

        val extended = reminder.extendedBy(ParkingReminder.EXTENSION_MILLIS, NOW)

        assertEquals(NOW + ParkingReminder.EXTENSION_MILLIS, extended.expiresAtMillis)
    }

    private companion object {
        const val PARKED_CAR_ID = 7L
        const val NOW = 1_700_000_000_000L
        val FIVE_MINUTES: Long = TimeUnit.MINUTES.toMillis(5)
        val FIFTEEN_MINUTES: Long = TimeUnit.MINUTES.toMillis(15)
        val ONE_HOUR: Long = TimeUnit.HOURS.toMillis(1)
        val TWO_HOURS: Long = TimeUnit.HOURS.toMillis(2)
    }
}
