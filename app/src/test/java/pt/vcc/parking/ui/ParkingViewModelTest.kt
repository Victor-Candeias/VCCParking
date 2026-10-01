package pt.vcc.parking.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pt.vcc.parking.data.local.ParkingCache
import pt.vcc.parking.data.remote.OverpassAttempt
import pt.vcc.parking.data.remote.OverpassElement
import pt.vcc.parking.data.remote.OverpassError
import pt.vcc.parking.data.remote.OverpassResult
import pt.vcc.parking.data.remote.OverpassService
import pt.vcc.parking.data.repository.ParkingRepository
import pt.vcc.parking.domain.model.Parking
import pt.vcc.parking.location.UserLocation

@OptIn(ExperimentalCoroutinesApi::class)
class ParkingViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts idle until there is a location`() {
        val viewModel = viewModel(RecordingOverpassService(success(NEAR)))

        assertEquals(ParkingUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `the first location triggers a search`() {
        val remote = RecordingOverpassService(success(NEAR))
        val viewModel = viewModel(remote)

        viewModel.onUserLocation(userLocation())

        assertEquals(1, remote.calls.size)
        assertEquals(SearchRadius.DEFAULT_METERS, remote.calls.single().radiusMeters)
        val state = viewModel.uiState.value as ParkingUiState.Success
        assertFalse(state.fromCache)
        assertEquals(listOf("node/1"), state.parking.map { it.id })
    }

    @Test
    fun `a small move does not repeat the search`() {
        val remote = RecordingOverpassService(success(NEAR))
        val viewModel = viewModel(remote)

        viewModel.onUserLocation(userLocation())
        viewModel.onUserLocation(userLocation(latitude = LATITUDE + 0.0004))

        assertEquals(1, remote.calls.size)
    }

    @Test
    fun `a significant move repeats the search at the new position`() {
        val remote = RecordingOverpassService(success(NEAR))
        val viewModel = viewModel(remote)

        viewModel.onUserLocation(userLocation())
        viewModel.onUserLocation(userLocation(latitude = LATITUDE + 0.01))

        assertEquals(2, remote.calls.size)
        assertEquals(LATITUDE + 0.01, remote.calls.last().latitude, 1e-9)
    }

    @Test
    fun `changing the radius repeats the search with the new radius`() {
        val remote = RecordingOverpassService(success(NEAR))
        val viewModel = viewModel(remote)

        viewModel.onUserLocation(userLocation())
        viewModel.onRadiusSelected(2_000)

        assertEquals(2_000, viewModel.radiusMeters.value)
        assertEquals(2_000, remote.calls.last().radiusMeters)
    }

    @Test
    fun `selecting the current radius changes nothing`() {
        val remote = RecordingOverpassService(success(NEAR))
        val viewModel = viewModel(remote)

        viewModel.onUserLocation(userLocation())
        viewModel.onRadiusSelected(SearchRadius.DEFAULT_METERS)

        assertEquals(1, remote.calls.size)
    }

    @Test
    fun `an area search measures distances from the user and not from the area`() {
        val remote = RecordingOverpassService(success(NEAR))
        val viewModel = viewModel(remote)

        viewModel.onUserLocation(userLocation())
        viewModel.searchArea(FAR_LATITUDE, FAR_LONGITUDE)

        assertEquals(FAR_LATITUDE, remote.calls.last().latitude, 1e-9)
        val state = viewModel.uiState.value as ParkingUiState.Success
        // NEAR fica a cerca de 111 m do utilizador e a mais de 2 km da area pesquisada.
        assertEquals(111.0, state.parking.single().distanceMeters!!, 10.0)
    }

    @Test
    fun `an unavailable overpass without cache maps to the error state`() {
        val viewModel = viewModel(RecordingOverpassService(failure()))

        viewModel.onUserLocation(userLocation())

        assertEquals(ParkingUiState.Error, viewModel.uiState.value)
    }

    @Test
    fun `retry repeats the search after an error`() {
        val remote = RecordingOverpassService(failure())
        val viewModel = viewModel(remote)

        viewModel.onUserLocation(userLocation())
        viewModel.retry()

        assertEquals(2, remote.calls.size)
    }

    @Test
    fun `retry without any location does nothing`() {
        val remote = RecordingOverpassService(success(NEAR))
        val viewModel = viewModel(remote)

        viewModel.retry()

        assertEquals(0, remote.calls.size)
        assertEquals(ParkingUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `cache older than the freshness window is reported as stale`() {
        val fresh = ParkingUiState.Success(emptyList(), fromCache = true, updatedAtMillis = NOW)
        val old = ParkingUiState.Success(
            parking = emptyList(),
            fromCache = true,
            updatedAtMillis = NOW - ParkingRepository.FRESHNESS_WINDOW_MILLIS,
        )
        val remote = ParkingUiState.Success(emptyList(), fromCache = false, updatedAtMillis = 0L)

        assertFalse(fresh.isStale(NOW))
        assertTrue(old.isStale(NOW))
        // Sem cache nao ha nada de desatualizado a assinalar, por mais antigo que seja.
        assertFalse(remote.isStale(NOW))
    }

    private fun viewModel(
        remote: OverpassService,
        cache: ParkingCache = EmptyParkingCache(),
    ) = ParkingViewModel(ParkingRepository(remote = remote, cache = cache, now = { NOW }))

    private fun userLocation(
        latitude: Double = LATITUDE,
        longitude: Double = LONGITUDE,
    ) = UserLocation(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = 12f,
        timestampMillis = NOW,
    )

    private class RecordingOverpassService(private val result: OverpassResult) : OverpassService {

        val calls = mutableListOf<Call>()

        override suspend fun searchParking(
            latitude: Double,
            longitude: Double,
            radiusMeters: Int,
        ): OverpassResult {
            calls += Call(latitude, longitude, radiusMeters)
            return result
        }

        data class Call(val latitude: Double, val longitude: Double, val radiusMeters: Int)
    }

    private class EmptyParkingCache : ParkingCache {

        override suspend fun nearby(
            latitude: Double,
            longitude: Double,
            radiusMeters: Int,
        ): List<Parking> = emptyList()

        override suspend fun latestUpdateAt(
            latitude: Double,
            longitude: Double,
            radiusMeters: Int,
        ): Long? = null

        override suspend fun save(parking: List<Parking>, updatedAtMillis: Long) = Unit

        override suspend fun deleteOlderThan(thresholdMillis: Long) = Unit
    }

    private companion object {
        const val NOW = 1_700_000_000_000L

        const val LATITUDE = 38.7169
        const val LONGITUDE = -9.1399
        const val FAR_LATITUDE = 38.7369
        const val FAR_LONGITUDE = -9.1427
        const val ENDPOINT = "https://overpass-api.de/api/interpreter"

        val NEAR = OverpassElement(
            type = "node",
            id = 1L,
            lat = LATITUDE + 0.001,
            lon = LONGITUDE,
            tags = emptyMap(),
        )

        fun success(vararg elements: OverpassElement) =
            OverpassResult.Success(ENDPOINT, elements.toList())

        fun failure() =
            OverpassResult.Failure(listOf(OverpassAttempt(ENDPOINT, OverpassError.Timeout)))
    }
}
