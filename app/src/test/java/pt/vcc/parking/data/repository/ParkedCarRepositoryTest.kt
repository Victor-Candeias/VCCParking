package pt.vcc.parking.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.vcc.parking.data.local.FakeParkedCarDao
import pt.vcc.parking.domain.model.Parking

class ParkedCarRepositoryTest {

    @Test
    fun `parking again ends the previous record instead of deleting it`() = runBlocking {
        val dao = FakeParkedCarDao()
        val clock = MutableClock(FIRST_PARK_MILLIS)
        val repository = ParkedCarRepository(dao, clock)

        val first = repository.park(LATITUDE, LONGITUDE)
        clock.millis = SECOND_PARK_MILLIS
        val second = repository.park(LATITUDE + 0.01, LONGITUDE + 0.01)

        val all = repository.observeAll().first()
        assertEquals(2, all.size)
        assertEquals(SECOND_PARK_MILLIS, all.first { it.id == first.id }.endedAtMillis)
        assertNull(all.first { it.id == second.id }.endedAtMillis)
    }

    @Test
    fun `at most one record stays active`() = runBlocking {
        val dao = FakeParkedCarDao()
        val clock = MutableClock(FIRST_PARK_MILLIS)
        val repository = ParkedCarRepository(dao, clock)

        repeat(3) { index ->
            clock.millis = FIRST_PARK_MILLIS + index * ONE_HOUR_MILLIS
            repository.park(LATITUDE, LONGITUDE)
        }

        val active = repository.observeAll().first().filter { it.isActive }
        assertEquals(1, active.size)
    }

    @Test
    fun `observeActive emits after parking and after ending`() = runBlocking {
        val dao = FakeParkedCarDao()
        val repository = ParkedCarRepository(dao, MutableClock(FIRST_PARK_MILLIS))

        assertNull(repository.observeActive().first())

        repository.park(LATITUDE, LONGITUDE, accuracyMeters = 8f)
        assertNotNull(repository.observeActive().first())

        repository.endActive()
        assertNull(repository.observeActive().first())
    }

    @Test
    fun `ending without an active record reports that nothing happened`() = runBlocking {
        val repository = ParkedCarRepository(FakeParkedCarDao(), MutableClock(FIRST_PARK_MILLIS))

        assertFalse(repository.endActive())

        repository.park(LATITUDE, LONGITUDE)
        assertTrue(repository.endActive())
        assertFalse(repository.endActive())
    }

    @Test
    fun `ending keeps the record in the history`() = runBlocking {
        val repository = ParkedCarRepository(FakeParkedCarDao(), MutableClock(FIRST_PARK_MILLIS))

        repository.park(LATITUDE, LONGITUDE)
        repository.endActive()

        assertEquals(1, repository.observeAll().first().size)
    }

    @Test
    fun `blank text is stored as missing information`() = runBlocking {
        val repository = ParkedCarRepository(FakeParkedCarDao(), MutableClock(FIRST_PARK_MILLIS))

        val parked = repository.park(LATITUDE, LONGITUDE, note = "   ", photoUri = "")

        assertNull(parked.note)
        assertNull(parked.photoUri)

        val updated = repository.updateDetails(parked.id, note = "  Piso -2  ", photoUri = " ")
        assertEquals("Piso -2", updated?.note)
        assertNull(updated?.photoUri)
    }

    @Test
    fun `the record keeps the identity of the chosen parking`() = runBlocking {
        val repository = ParkedCarRepository(FakeParkedCarDao(), MutableClock(FIRST_PARK_MILLIS))

        val parked = repository.park(
            latitude = LATITUDE,
            longitude = LONGITUDE,
            parking = Parking(osmId = 42, osmType = "way", latitude = 1.0, longitude = 2.0),
        )

        assertEquals("way/42", parked.parkingId)
        // A posicao guardada e a recebida, nao a do parque.
        assertEquals(LATITUDE, parked.latitude, 0.0)
    }

    @Test
    fun `correcting the position edits the record and clears the accuracy`() = runBlocking {
        val repository = ParkedCarRepository(FakeParkedCarDao(), MutableClock(FIRST_PARK_MILLIS))

        val parked = repository.park(LATITUDE, LONGITUDE, accuracyMeters = 140f)
        val corrected = repository.updatePosition(parked.id, LATITUDE + 0.002, LONGITUDE - 0.002)

        assertEquals(parked.id, corrected?.id)
        assertEquals(LATITUDE + 0.002, corrected?.latitude ?: 0.0, 1e-9)
        assertNull(corrected?.accuracyMeters)
        assertEquals(parked.parkedAtMillis, corrected?.parkedAtMillis)
        // Nao pode nascer um registo novo: seria uma paragem que nunca aconteceu.
        assertEquals(1, repository.observeAll().first().size)
    }

    private class MutableClock(var millis: Long) : () -> Long {
        override fun invoke(): Long = millis
    }

    private companion object {
        const val LATITUDE = 38.7336
        const val LONGITUDE = -9.1447
        const val FIRST_PARK_MILLIS = 1_700_000_000_000L
        const val ONE_HOUR_MILLIS = 3_600_000L
        const val SECOND_PARK_MILLIS = FIRST_PARK_MILLIS + ONE_HOUR_MILLIS
    }
}
