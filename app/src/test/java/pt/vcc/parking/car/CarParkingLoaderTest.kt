package pt.vcc.parking.car

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.vcc.parking.data.local.ParkingCache
import pt.vcc.parking.data.remote.OverpassAttempt
import pt.vcc.parking.data.remote.OverpassElement
import pt.vcc.parking.data.remote.OverpassError
import pt.vcc.parking.data.remote.OverpassResult
import pt.vcc.parking.data.remote.OverpassService
import pt.vcc.parking.data.repository.ParkingRepository
import pt.vcc.parking.domain.model.Parking
import pt.vcc.parking.location.LocationProvider
import pt.vcc.parking.location.LocationResult
import pt.vcc.parking.location.UserLocation

/**
 * O `CarParkingLoader` existe precisamente para que estas decisoes sejam
 * verificaveis sem o SDK do carro; estes testes sao a razao de ser da separacao.
 */
class CarParkingLoaderTest {

    @Test
    fun `a search with results reports the user position`() = runBlocking {
        val loader = loader(location = available(), remote = success(PUBLIC))

        val state = loader.load() as CarParkingUiState.Ready

        assertEquals(listOf("node/1"), state.parking.map { it.id })
        assertEquals(LATITUDE, state.latitude, 0.0)
        assertEquals(LONGITUDE, state.longitude, 0.0)
    }

    @Test
    fun `missing permission is its own state and not a failure`() = runBlocking {
        val loader = loader(location = LocationResult.PermissionMissing, remote = success(PUBLIC))

        assertEquals(CarParkingUiState.PermissionMissing, loader.load())
    }

    @Test
    fun `disabled location does not reach the network`() = runBlocking {
        val remote = FakeOverpassService(success(PUBLIC))
        val loader = loader(location = LocationResult.LocationDisabled, remote = remote)

        assertEquals(CarParkingUiState.LocationUnavailable, loader.load())
        assertEquals(0, remote.calls)
    }

    @Test
    fun `an unusable reading is reported as location unavailable`() = runBlocking {
        val loader = loader(location = LocationResult.Unavailable(), remote = success(PUBLIC))

        assertEquals(CarParkingUiState.LocationUnavailable, loader.load())
    }

    @Test
    fun `an empty search is distinguished from a failure`() = runBlocking {
        val loader = loader(location = available(), remote = success())

        assertEquals(CarParkingUiState.Empty, loader.load())
    }

    @Test
    fun `without cache an unavailable overpass is a failure`() = runBlocking {
        val loader = loader(location = available(), remote = failure())

        assertEquals(CarParkingUiState.Failure, loader.load())
    }

    @Test
    fun `private parking never reaches the car`() = runBlocking {
        val loader = loader(location = available(), remote = success(PUBLIC, PRIVATE))

        val state = loader.load() as CarParkingUiState.Ready

        assertEquals(listOf("node/1"), state.parking.map { it.id })
    }

    @Test
    fun `the list reaches the car sorted by distance`() = runBlocking {
        val loader = loader(location = available(), remote = success(FAR, PUBLIC))

        val state = loader.load() as CarParkingUiState.Ready

        assertEquals(listOf("node/1", "node/3"), state.parking.map { it.id })
        assertTrue(state.parking.all { it.distanceMeters != null })
    }

    @Test
    fun `a forced refresh ignores the freshness window`() = runBlocking {
        val remote = FakeOverpassService(success(PUBLIC))
        val cache = FakeParkingCache(updatedAt = System.currentTimeMillis())
        val loader = loader(location = available(), remote = remote, cache = cache)

        loader.load(forceRefresh = true)

        assertEquals(1, remote.calls)
    }

    private fun loader(
        location: LocationResult,
        remote: OverpassResult,
        cache: ParkingCache = FakeParkingCache(),
    ) = loader(location, FakeOverpassService(remote), cache)

    private fun loader(
        location: LocationResult,
        remote: OverpassService,
        cache: ParkingCache = FakeParkingCache(),
    ) = CarParkingLoader(
        location = FakeLocationProvider(location),
        repository = ParkingRepository(remote = remote, cache = cache),
    )

    private fun available() = LocationResult.Available(
        UserLocation(
            latitude = LATITUDE,
            longitude = LONGITUDE,
            accuracyMeters = 10f,
            timestampMillis = 0L,
        ),
    )

    private fun success(vararg elements: OverpassElement) =
        OverpassResult.Success(endpoint = "fake", elements = elements.toList())

    private fun failure() = OverpassResult.Failure(
        listOf(OverpassAttempt(endpoint = "fake", error = OverpassError.Network)),
    )

    private class FakeLocationProvider(private val result: LocationResult) : LocationProvider {
        override suspend fun currentLocation(): LocationResult = result
    }

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

    private class FakeParkingCache(private val updatedAt: Long? = null) : ParkingCache {
        override suspend fun save(parking: List<Parking>, updatedAtMillis: Long) = Unit

        override suspend fun nearby(
            latitude: Double,
            longitude: Double,
            radiusMeters: Int,
        ): List<Parking> = emptyList()

        override suspend fun latestUpdateAt(
            latitude: Double,
            longitude: Double,
            radiusMeters: Int,
        ): Long? = updatedAt

        override suspend fun deleteOlderThan(thresholdMillis: Long) = Unit
    }

    private companion object {
        const val LATITUDE = 38.7223
        const val LONGITUDE = -9.1393

        val PUBLIC = OverpassElement(
            type = "node",
            id = 1,
            lat = 38.7224,
            lon = -9.1394,
            tags = mapOf("amenity" to "parking", "name" to "Parque Central"),
        )

        val PRIVATE = OverpassElement(
            type = "node",
            id = 2,
            lat = 38.7225,
            lon = -9.1395,
            tags = mapOf("amenity" to "parking", "access" to "private"),
        )

        val FAR = OverpassElement(
            type = "node",
            id = 3,
            lat = 38.7400,
            lon = -9.1600,
            tags = mapOf("amenity" to "parking"),
        )
    }
}
