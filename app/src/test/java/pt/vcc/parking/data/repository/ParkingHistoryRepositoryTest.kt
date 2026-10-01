package pt.vcc.parking.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.vcc.parking.data.local.FakeParkedCarDao
import pt.vcc.parking.data.local.HistoryRetention
import pt.vcc.parking.data.local.HistorySettings
import pt.vcc.parking.data.local.ParkedCarEntity
import pt.vcc.parking.data.local.ParkedPhotoStore

class ParkingHistoryRepositoryTest {

    @Test
    fun `the history only shows records that have ended`() = runBlocking {
        val fixture = Fixture()
        fixture.park(PARKED_AT_MILLIS, endedAtMillis = PARKED_AT_MILLIS + ONE_HOUR_MILLIS)
        fixture.park(NOW_MILLIS, endedAtMillis = null)

        val history = fixture.repository.observeHistory(limit = 10).first()

        assertEquals(1, history.size)
        assertEquals(PARKED_AT_MILLIS, history.single().parkedAtMillis)
        assertEquals(1, fixture.repository.observeCount().first())
    }

    @Test
    fun `the page only reads as far as the limit`() = runBlocking {
        val fixture = Fixture()
        repeat(5) { index ->
            val parkedAt = PARKED_AT_MILLIS + index * ONE_HOUR_MILLIS
            fixture.park(parkedAt, endedAtMillis = parkedAt + ONE_HOUR_MILLIS)
        }

        assertEquals(2, fixture.repository.observeHistory(limit = 2).first().size)
        assertEquals(5, fixture.repository.observeCount().first())
    }

    @Test
    fun `deleting a record keeps the photo until the removal is final`() = runBlocking {
        val fixture = Fixture()
        val id = fixture.park(
            PARKED_AT_MILLIS,
            endedAtMillis = PARKED_AT_MILLIS + ONE_HOUR_MILLIS,
            photoUri = PHOTO_URI,
        )

        val removed = fixture.repository.delete(id)

        assertEquals(PHOTO_URI, removed?.photoUri)
        assertEquals(0, fixture.repository.observeCount().first())
        // Enquanto a remocao puder ser anulada, o ficheiro ainda faz falta.
        assertTrue(fixture.removedPhotos.isEmpty())

        fixture.repository.discardPhoto(removed!!)
        assertEquals(listOf(PHOTO_URI), fixture.removedPhotos)
    }

    @Test
    fun `undoing a removal brings the record back with the same identity`() = runBlocking {
        val fixture = Fixture()
        val id = fixture.park(PARKED_AT_MILLIS, endedAtMillis = PARKED_AT_MILLIS + ONE_HOUR_MILLIS)

        val removed = fixture.repository.delete(id)!!
        val restored = fixture.repository.restore(removed)

        assertEquals(id, restored.id)
        assertEquals(1, fixture.repository.observeCount().first())
    }

    @Test
    fun `the active record is never deleted from the history`() = runBlocking {
        val fixture = Fixture()
        val activeId = fixture.park(NOW_MILLIS, endedAtMillis = null)

        assertNull(fixture.repository.delete(activeId))
        assertEquals(0, fixture.repository.deleteAll())
        assertNotNull(fixture.dao.active())
    }

    @Test
    fun `clearing the history deletes the records and their photos`() = runBlocking {
        val fixture = Fixture()
        fixture.park(
            PARKED_AT_MILLIS,
            endedAtMillis = PARKED_AT_MILLIS + ONE_HOUR_MILLIS,
            photoUri = PHOTO_URI,
        )
        fixture.park(NOW_MILLIS, endedAtMillis = null)

        assertEquals(1, fixture.repository.deleteAll())
        assertEquals(0, fixture.repository.observeCount().first())
        assertEquals(listOf(PHOTO_URI), fixture.removedPhotos)
    }

    @Test
    fun `retention deletes what is older than the chosen period`() = runBlocking {
        val fixture = Fixture()
        val old = NOW_MILLIS - 100 * ONE_DAY_MILLIS
        fixture.park(old, endedAtMillis = old + ONE_HOUR_MILLIS, photoUri = PHOTO_URI)
        val recent = NOW_MILLIS - ONE_DAY_MILLIS
        fixture.park(recent, endedAtMillis = recent + ONE_HOUR_MILLIS)

        assertEquals(1, fixture.repository.applyRetention())

        val remaining = fixture.repository.history()
        assertEquals(1, remaining.size)
        assertEquals(recent, remaining.single().parkedAtMillis)
        assertEquals(listOf(PHOTO_URI), fixture.removedPhotos)
    }

    @Test
    fun `retention never touches the record still in progress`() = runBlocking {
        val fixture = Fixture()
        // Comecou ha muito tempo, mas ainda nao terminou: o carro continua la.
        fixture.park(NOW_MILLIS - 400 * ONE_DAY_MILLIS, endedAtMillis = null)

        assertEquals(0, fixture.repository.applyRetention())
        assertEquals(1, fixture.dao.observeAll().first().size)
    }

    @Test
    fun `keeping forever disables the cleanup`() = runBlocking {
        val fixture = Fixture()
        val old = NOW_MILLIS - 1_000 * ONE_DAY_MILLIS
        fixture.park(old, endedAtMillis = old + ONE_HOUR_MILLIS)

        fixture.repository.setRetention(HistoryRetention.Forever)

        assertEquals(0, fixture.repository.applyRetention())
        assertEquals(1, fixture.repository.history().size)
    }

    @Test
    fun `changing the retention applies it right away`() = runBlocking {
        val fixture = Fixture()
        val old = NOW_MILLIS - 45 * ONE_DAY_MILLIS
        fixture.park(old, endedAtMillis = old + ONE_HOUR_MILLIS)

        assertEquals(1, fixture.repository.history().size)

        fixture.repository.setRetention(HistoryRetention.Days30)

        assertEquals(0, fixture.repository.history().size)
        assertEquals(HistoryRetention.Days30, fixture.repository.retention.first())
    }

    private class Fixture {
        val dao = FakeParkedCarDao()
        val removedPhotos = mutableListOf<String>()
        val settings = InMemoryHistorySettings()

        val repository = ParkingHistoryRepository(
            dao = dao,
            settings = settings,
            photos = ParkedPhotoStore { removedPhotos += it },
            now = { NOW_MILLIS },
        )

        suspend fun park(
            parkedAtMillis: Long,
            endedAtMillis: Long?,
            photoUri: String? = null,
        ): Long = dao.insert(
            ParkedCarEntity(
                latitude = LATITUDE,
                longitude = LONGITUDE,
                parkedAtMillis = parkedAtMillis,
                endedAtMillis = endedAtMillis,
                photoUri = photoUri,
            ),
        )
    }

    private class InMemoryHistorySettings : HistorySettings {
        private val state = MutableStateFlow(HistoryRetention.Default)
        override val retention = state

        override suspend fun setRetention(retention: HistoryRetention) {
            state.value = retention
        }
    }

    private companion object {
        const val LATITUDE = 38.7336
        const val LONGITUDE = -9.1447
        const val PHOTO_URI = "file:///data/parked/parked-1.jpg"
        const val NOW_MILLIS = 1_700_000_000_000L
        const val ONE_HOUR_MILLIS = 3_600_000L
        const val ONE_DAY_MILLIS = 86_400_000L
        const val PARKED_AT_MILLIS = NOW_MILLIS - 2 * ONE_HOUR_MILLIS
    }
}
