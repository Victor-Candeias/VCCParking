package pt.vcc.parking.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.vcc.parking.data.local.ParkingCache
import pt.vcc.parking.data.remote.OverpassAttempt
import pt.vcc.parking.data.remote.OverpassElement
import pt.vcc.parking.data.remote.OverpassError
import pt.vcc.parking.data.remote.OverpassResult
import pt.vcc.parking.data.remote.OverpassService
import pt.vcc.parking.domain.model.Parking

class ParkingRepositoryTest {

    @Test
    fun `fresh cache is served without calling overpass`() = runBlocking {
        val remote = FakeOverpassService(success(PUBLIC_NEAR))
        val cache = FakeParkingCache(listOf(cached(PUBLIC_NEAR)), updatedAt = NOW - FIVE_MINUTES)
        val repository = repository(remote, cache)

        val result = repository.parkingNear(LATITUDE, LONGITUDE) as ParkingResult.Success

        assertEquals(0, remote.calls)
        assertTrue(result.fromCache)
        assertEquals(NOW - FIVE_MINUTES, result.updatedAtMillis)
        assertEquals(listOf("node/1"), result.parking.map { it.id })
    }

    @Test
    fun `force refresh ignores the freshness window`() = runBlocking {
        val remote = FakeOverpassService(success(PUBLIC_NEAR))
        val cache = FakeParkingCache(listOf(cached(PUBLIC_NEAR)), updatedAt = NOW - FIVE_MINUTES)
        val repository = repository(remote, cache)

        val result = repository.parkingNear(LATITUDE, LONGITUDE, forceRefresh = true)

        assertEquals(1, remote.calls)
        assertFalse((result as ParkingResult.Success).fromCache)
    }

    @Test
    fun `stale cache triggers a remote search`() = runBlocking {
        val remote = FakeOverpassService(success(PUBLIC_NEAR))
        val cache = FakeParkingCache(listOf(cached(PUBLIC_NEAR)), updatedAt = NOW - SIXTEEN_MINUTES)
        val repository = repository(remote, cache)

        val result = repository.parkingNear(LATITUDE, LONGITUDE)

        assertEquals(1, remote.calls)
        assertFalse((result as ParkingResult.Success).fromCache)
    }

    @Test
    fun `a successful search saves the cache and reports remote data`() = runBlocking {
        val remote = FakeOverpassService(success(PUBLIC_NEAR, PUBLIC_FAR))
        val cache = FakeParkingCache()
        val repository = repository(remote, cache)

        val result = repository.parkingNear(LATITUDE, LONGITUDE) as ParkingResult.Success

        assertFalse(result.fromCache)
        assertEquals(NOW, result.updatedAtMillis)
        assertEquals(NOW, cache.savedAt)
        assertEquals(listOf("node/1", "node/2"), cache.stored.map { it.id })
    }

    @Test
    fun `private parking is hidden on both the remote and the cache paths`() = runBlocking {
        val remote = FakeOverpassService(success(PUBLIC_NEAR, PRIVATE_NEAR))
        val cache = FakeParkingCache()
        val repository = repository(remote, cache)

        val remoteResult = repository.parkingNear(LATITUDE, LONGITUDE) as ParkingResult.Success
        assertEquals(listOf("node/1"), remoteResult.parking.map { it.id })

        // Seccao 10: o filtro e do cliente, por isso o privado fica na cache.
        assertEquals(listOf("node/1", "node/3"), cache.stored.map { it.id })

        val cachedResult = repository(FakeOverpassService(failure()), cache)
            .parkingNear(LATITUDE, LONGITUDE) as ParkingResult.Success
        assertEquals(listOf("node/1"), cachedResult.parking.map { it.id })
    }

    @Test
    fun `results are sorted by distance with the computed distance filled in`() = runBlocking {
        val remote = FakeOverpassService(success(PUBLIC_FAR, PUBLIC_NEAR))
        val repository = repository(remote, FakeParkingCache())

        val result = repository.parkingNear(LATITUDE, LONGITUDE) as ParkingResult.Success

        assertEquals(listOf("node/1", "node/2"), result.parking.map { it.id })
        assertNotNull(result.parking.first().distanceMeters)
        assertTrue(result.parking[0].distanceMeters!! < result.parking[1].distanceMeters!!)
    }

    @Test
    fun `retention runs after every save`() = runBlocking {
        val remote = FakeOverpassService(success(PUBLIC_NEAR))
        val cache = FakeParkingCache()

        repository(remote, cache).parkingNear(LATITUDE, LONGITUDE)

        assertEquals(NOW - ParkingRepository.RETENTION_MILLIS, cache.deletedBefore)
    }

    @Test
    fun `an unavailable overpass falls back to the cache`() = runBlocking {
        val remote = FakeOverpassService(failure())
        val cache = FakeParkingCache(listOf(cached(PUBLIC_NEAR)), updatedAt = NOW - SIXTEEN_MINUTES)

        val result = repository(remote, cache).parkingNear(LATITUDE, LONGITUDE)

        val success = result as ParkingResult.Success
        assertTrue(success.fromCache)
        assertEquals(NOW - SIXTEEN_MINUTES, success.updatedAtMillis)
        assertEquals(listOf("node/1"), success.parking.map { it.id })
    }

    @Test
    fun `an unavailable overpass without cache reports the attempts`() = runBlocking {
        val remote = FakeOverpassService(failure())

        val result = repository(remote, FakeParkingCache()).parkingNear(LATITUDE, LONGITUDE)

        val failure = result as ParkingResult.Failure
        assertEquals(listOf(ENDPOINT), failure.attempts.map { it.endpoint })
    }

    @Test
    fun `cancellation is propagated instead of being treated as a failure`() {
        val remote = object : OverpassService {
            override suspend fun searchParking(
                latitude: Double,
                longitude: Double,
                radiusMeters: Int,
            ): OverpassResult = throw CancellationException("cancelado")
        }

        assertThrows(CancellationException::class.java) {
            runBlocking { repository(remote, FakeParkingCache()).parkingNear(LATITUDE, LONGITUDE) }
        }
    }

    private fun repository(remote: OverpassService, cache: ParkingCache) =
        ParkingRepository(remote = remote, cache = cache, now = { NOW })

    private class FakeOverpassService(private val result: OverpassResult) : OverpassService {

        var calls = 0
            private set

        override suspend fun searchParking(
            latitude: Double,
            longitude: Double,
            radiusMeters: Int,
        ): OverpassResult {
            calls++
            return result
        }
    }

    private class FakeParkingCache(
        initial: List<Parking> = emptyList(),
        private var updatedAt: Long? = null,
    ) : ParkingCache {

        val stored: MutableList<Parking> = initial.toMutableList()
        var savedAt: Long? = null
            private set
        var deletedBefore: Long? = null
            private set

        override suspend fun nearby(
            latitude: Double,
            longitude: Double,
            radiusMeters: Int,
        ): List<Parking> = stored.toList()

        override suspend fun latestUpdateAt(
            latitude: Double,
            longitude: Double,
            radiusMeters: Int,
        ): Long? = updatedAt

        override suspend fun save(parking: List<Parking>, updatedAtMillis: Long) {
            stored.clear()
            stored += parking
            savedAt = updatedAtMillis
            updatedAt = updatedAtMillis
        }

        override suspend fun deleteOlderThan(thresholdMillis: Long) {
            deletedBefore = thresholdMillis
        }
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val FIVE_MINUTES = 5L * 60 * 1_000
        const val SIXTEEN_MINUTES = 16L * 60 * 1_000

        const val LATITUDE = 38.7169
        const val LONGITUDE = -9.1399
        const val ENDPOINT = "https://overpass-api.de/api/interpreter"

        val PUBLIC_NEAR = element(1L, 38.7179, -9.1399)
        val PUBLIC_FAR = element(2L, 38.7369, -9.1427)
        val PRIVATE_NEAR = element(3L, 38.7175, -9.1395, mapOf("access" to "private"))

        fun element(
            id: Long,
            latitude: Double,
            longitude: Double,
            tags: Map<String, String> = emptyMap(),
        ) = OverpassElement(type = "node", id = id, lat = latitude, lon = longitude, tags = tags)

        fun success(vararg elements: OverpassElement) =
            OverpassResult.Success(ENDPOINT, elements.toList())

        fun failure() =
            OverpassResult.Failure(listOf(OverpassAttempt(ENDPOINT, OverpassError.Timeout)))

        fun cached(element: OverpassElement) = Parking(
            osmId = element.id,
            osmType = element.type,
            latitude = element.lat!!,
            longitude = element.lon!!,
            access = element.tags["access"],
        )
    }
}
