package pt.vcc.parking.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * O teste do DAO e instrumentado porque o Room precisa de um contexto Android;
 * os testes do repositorio ficam na JVM gracas a interface `ParkingCache`.
 */
@RunWith(AndroidJUnit4::class)
class ParkingDaoTest {

    private lateinit var database: ParkingDatabase
    private lateinit var dao: ParkingDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ParkingDatabase::class.java,
        ).build()
        dao = database.parkingDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun savingTheSameKeyTwiceKeepsASingleRow() = runBlocking {
        dao.upsertAll(listOf(entity(1L, name = "Antigo", updatedAt = OLD)))
        dao.upsertAll(listOf(entity(1L, name = "Novo", updatedAt = NOW)))

        val stored = dao.nearby(box.minLatitude, box.maxLatitude, box.minLongitude, box.maxLongitude)

        assertEquals(1, stored.size)
        assertEquals("Novo", stored.single().name)
        assertEquals(NOW, stored.single().updatedAt)
    }

    @Test
    fun theSameIdInADifferentTypeIsADifferentRow() = runBlocking {
        dao.upsertAll(
            listOf(
                entity(1L, osmType = "node"),
                entity(1L, osmType = "way"),
            ),
        )

        val stored = dao.nearby(box.minLatitude, box.maxLatitude, box.minLongitude, box.maxLongitude)

        assertEquals(setOf("node", "way"), stored.map { it.osmType }.toSet())
    }

    @Test
    fun theBoundingBoxIncludesAParkingAtTheEdgeOfTheRadius() = runBlocking {
        // Cerca de 990 m a norte, dentro do raio de 1000 m.
        val edge = entity(2L, latitude = LATITUDE + 990 / 111_320.0)
        dao.upsertAll(listOf(entity(1L), edge))

        val stored = dao.nearby(box.minLatitude, box.maxLatitude, box.minLongitude, box.maxLongitude)

        assertEquals(setOf(1L, 2L), stored.map { it.osmId }.toSet())
    }

    @Test
    fun theBoundingBoxExcludesParkingWellOutsideTheRadius() = runBlocking {
        dao.upsertAll(listOf(entity(1L), entity(2L, latitude = LATITUDE + 1.0)))

        val stored = dao.nearby(box.minLatitude, box.maxLatitude, box.minLongitude, box.maxLongitude)

        assertEquals(listOf(1L), stored.map { it.osmId })
    }

    @Test
    fun latestUpdateAtReturnsTheMostRecentSaveInsideTheBox() = runBlocking {
        dao.upsertAll(listOf(entity(1L, updatedAt = OLD), entity(2L, updatedAt = NOW)))

        val latest = dao.latestUpdateAt(
            box.minLatitude,
            box.maxLatitude,
            box.minLongitude,
            box.maxLongitude,
        )

        assertEquals(NOW, latest)
    }

    @Test
    fun latestUpdateAtIsNullWithoutCacheInTheArea() = runBlocking {
        dao.upsertAll(listOf(entity(1L, latitude = LATITUDE + 1.0)))

        val latest = dao.latestUpdateAt(
            box.minLatitude,
            box.maxLatitude,
            box.minLongitude,
            box.maxLongitude,
        )

        assertNull(latest)
    }

    @Test
    fun retentionOnlyDeletesRowsOlderThanTheThreshold() = runBlocking {
        dao.upsertAll(listOf(entity(1L, updatedAt = OLD), entity(2L, updatedAt = NOW)))

        dao.deleteOlderThan(NOW)

        val stored = dao.nearby(box.minLatitude, box.maxLatitude, box.minLongitude, box.maxLongitude)
        assertEquals(listOf(2L), stored.map { it.osmId })
    }

    @Test
    fun optionalTagsSurviveAsNull() = runBlocking {
        dao.upsertAll(listOf(entity(1L)))

        val stored = dao.nearby(box.minLatitude, box.maxLatitude, box.minLongitude, box.maxLongitude)

        assertTrue(stored.single().capacity == null && stored.single().fee == null)
    }

    private val box get() = BoundingBox.around(LATITUDE, LONGITUDE, RADIUS_METERS)

    private fun entity(
        osmId: Long,
        osmType: String = "node",
        latitude: Double = LATITUDE,
        longitude: Double = LONGITUDE,
        name: String? = null,
        updatedAt: Long = NOW,
    ) = ParkingEntity(
        osmType = osmType,
        osmId = osmId,
        latitude = latitude,
        longitude = longitude,
        name = name,
        updatedAt = updatedAt,
    )

    private companion object {
        const val LATITUDE = 38.7169
        const val LONGITUDE = -9.1399
        const val RADIUS_METERS = 1_000
        const val NOW = 1_700_000_000_000L
        const val OLD = NOW - 8L * 24 * 60 * 60 * 1_000
    }
}
