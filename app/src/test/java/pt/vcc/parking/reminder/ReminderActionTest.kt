package pt.vcc.parking.reminder

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.vcc.parking.data.local.FakeParkedCarDao
import pt.vcc.parking.data.local.FakeParkingReminderDao
import pt.vcc.parking.data.local.ParkedCarEntity
import pt.vcc.parking.data.repository.ReminderRepository
import pt.vcc.parking.domain.model.ParkingReminder

/**
 * Accoes sobre lembretes de `vp-11-reminders`: definir, prolongar, dispensar e
 * limpar, incluindo o que o utilizador faz a partir da notificacao.
 */
class ReminderActionTest {

    @Test
    fun `setting a deadline stores the instant and not the duration`() = runBlocking {
        val world = World()
        val parkedCarId = world.park()

        val reminder = world.repository.setDeadline(parkedCarId, TWO_HOURS)

        assertEquals(NOW + TWO_HOURS, reminder.expiresAtMillis)
        assertEquals(reminder, world.repository.byParkedCarId(parkedCarId))
    }

    @Test
    fun `a new deadline silences a previously dismissed reminder`() = runBlocking {
        val world = World()
        val parkedCarId = world.park()
        world.repository.setDeadline(parkedCarId, TWO_HOURS)
        world.repository.dismiss(parkedCarId)

        val renewed = world.repository.setDeadline(parkedCarId, TWO_HOURS)

        assertFalse(renewed.dismissed)
    }

    @Test
    fun `extending from the notification adds to the current deadline`() = runBlocking {
        val world = World()
        val parkedCarId = world.park()
        world.repository.setDeadline(parkedCarId, TWO_HOURS)

        val extended = world.repository.extend(parkedCarId)

        assertEquals(
            NOW + TWO_HOURS + ParkingReminder.EXTENSION_MILLIS,
            extended?.expiresAtMillis,
        )
    }

    @Test
    fun `extending without a reminder does nothing`() = runBlocking {
        val world = World()
        val parkedCarId = world.park()

        assertNull(world.repository.extend(parkedCarId))
    }

    @Test
    fun `preferences survive a change of deadline`() = runBlocking {
        val world = World()
        val parkedCarId = world.park()
        world.repository.updatePreferences(parkedCarId, FIVE_MINUTES, TWO_HOURS)

        val reminder = world.repository.setDeadline(parkedCarId, TWO_HOURS, FIVE_MINUTES)

        assertEquals(FIVE_MINUTES, reminder.warnBeforeMillis)
        assertEquals(TWO_HOURS, reminder.recurringEveryMillis)
    }

    @Test
    fun `dismissing keeps the deadline but stops the alarms`() = runBlocking {
        val world = World()
        val parkedCarId = world.park()
        world.repository.setDeadline(parkedCarId, TWO_HOURS)

        assertTrue(world.repository.dismiss(parkedCarId))

        val reminder = requireNotNull(world.repository.byParkedCarId(parkedCarId))
        assertEquals(NOW + TWO_HOURS, reminder.expiresAtMillis)
        assertTrue(ReminderPlan.alarmsFor(reminder, NOW).isEmpty())
    }

    @Test
    fun `clearing removes the reminder`() = runBlocking {
        val world = World()
        val parkedCarId = world.park()
        world.repository.setDeadline(parkedCarId, TWO_HOURS)

        assertTrue(world.repository.clear(parkedCarId))

        assertNull(world.repository.byParkedCarId(parkedCarId))
    }

    @Test
    fun `the active reminder follows the active parking`() = runBlocking {
        val world = World()
        val parkedCarId = world.park()
        world.repository.setDeadline(parkedCarId, TWO_HOURS)

        assertEquals(NOW + TWO_HOURS, world.repository.observeActive().first()?.expiresAtMillis)

        world.parkedCars.endActive(NOW + FIVE_MINUTES)

        assertNull(world.repository.observeActive().first())
        assertNull(world.repository.activeReminder())
    }

    @Test
    fun `pending drops reminders of parkings that already ended`() = runBlocking {
        val world = World()
        val parkedCarId = world.park()
        world.repository.setDeadline(parkedCarId, TWO_HOURS)
        world.parkedCars.endActive(NOW + FIVE_MINUTES)

        assertTrue(world.repository.pending().isEmpty())
    }

    @Test
    fun `pending skips reminders with nothing left to announce`() = runBlocking {
        val world = World()
        val parkedCarId = world.park()
        // Sem prazo e sem repeticao: so preferencias guardadas.
        world.repository.updatePreferences(parkedCarId, FIVE_MINUTES, null)

        assertTrue(world.repository.pending().isEmpty())
    }

    @Test
    fun `pending returns the reminders to reschedule after a reboot`() = runBlocking {
        val world = World()
        val parkedCarId = world.park()
        world.repository.setDeadline(parkedCarId, TWO_HOURS)

        assertEquals(listOf(parkedCarId), world.repository.pending().map { it.parkedCarId })
    }

    private class World {
        val parkedCars = FakeParkedCarDao()
        val reminders = FakeParkingReminderDao(parkedCars)
        val repository = ReminderRepository(reminders, parkedCars) { NOW }

        suspend fun park(): Long = parkedCars.insert(
            ParkedCarEntity(latitude = LATITUDE, longitude = LONGITUDE, parkedAtMillis = NOW),
        )
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val LATITUDE = 38.736946
        const val LONGITUDE = -9.142685
        val FIVE_MINUTES: Long = TimeUnit.MINUTES.toMillis(5)
        val TWO_HOURS: Long = TimeUnit.HOURS.toMillis(2)
    }
}
