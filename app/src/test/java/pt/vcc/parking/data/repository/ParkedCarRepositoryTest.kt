package pt.vcc.parking.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.vcc.parking.data.local.ParkedCarDao
import pt.vcc.parking.data.local.ParkedCarEntity
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

    /**
     * Herda [ParkedCarDao.park] de proposito: a transacao que termina o anterior
     * antes de inserir o novo e codigo de producao e e o que estes testes cobrem.
     */
    private class FakeParkedCarDao : ParkedCarDao() {

        private val rows = MutableStateFlow<List<ParkedCarEntity>>(emptyList())
        private var nextId = 1L

        override suspend fun insert(parkedCar: ParkedCarEntity): Long {
            val id = nextId++
            rows.value = rows.value + parkedCar.copy(id = id)
            return id
        }

        override fun observeActive(): Flow<ParkedCarEntity?> = rows.map { it.activeRecord() }

        override suspend fun active(): ParkedCarEntity? = rows.value.activeRecord()

        override suspend fun byId(id: Long): ParkedCarEntity? =
            rows.value.firstOrNull { it.id == id }

        override fun observeAll(): Flow<List<ParkedCarEntity>> =
            rows.map { list -> list.sortedByDescending { it.parkedAtMillis } }

        override suspend fun endActive(endedAtMillis: Long): Int {
            val affected = rows.value.count { it.endedAtMillis == null }
            rows.value = rows.value.map { row ->
                if (row.endedAtMillis == null) row.copy(endedAtMillis = endedAtMillis) else row
            }
            return affected
        }

        override suspend fun updateDetails(id: Long, note: String?, photoUri: String?): Int =
            update(id) { it.copy(note = note, photoUri = photoUri) }

        override suspend fun updatePosition(id: Long, latitude: Double, longitude: Double): Int =
            update(id) {
                it.copy(latitude = latitude, longitude = longitude, accuracyMeters = null)
            }

        private fun update(id: Long, change: (ParkedCarEntity) -> ParkedCarEntity): Int {
            val affected = rows.value.count { it.id == id }
            rows.value = rows.value.map { if (it.id == id) change(it) else it }
            return affected
        }

        private fun List<ParkedCarEntity>.activeRecord(): ParkedCarEntity? =
            filter { it.endedAtMillis == null }.maxByOrNull { it.parkedAtMillis }
    }

    private companion object {
        const val LATITUDE = 38.7336
        const val LONGITUDE = -9.1447
        const val FIRST_PARK_MILLIS = 1_700_000_000_000L
        const val ONE_HOUR_MILLIS = 3_600_000L
        const val SECOND_PARK_MILLIS = FIRST_PARK_MILLIS + ONE_HOUR_MILLIS
    }
}
