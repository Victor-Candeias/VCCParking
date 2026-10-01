package pt.vcc.parking.parked

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pt.vcc.parking.data.local.ParkedCarDao
import pt.vcc.parking.data.local.ParkedCarEntity
import pt.vcc.parking.data.repository.ParkedCarRepository
import pt.vcc.parking.domain.model.Parking
import pt.vcc.parking.location.LocationProvider
import pt.vcc.parking.location.LocationResult
import pt.vcc.parking.location.UserLocation

@OptIn(ExperimentalCoroutinesApi::class)
class ParkedCarViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts without a parked car`() = test { viewModel, _ ->
        assertEquals(ParkedCarUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `parking here stores the measured position`() = test(
        location = LocationResult.Available(userLocation()),
    ) { viewModel, _ ->
        viewModel.parkHere()
        advanceUntilIdle()

        val parkedCar = (viewModel.uiState.value as ParkedCarUiState.Active).parkedCar
        assertEquals(LATITUDE, parkedCar.latitude, 0.0)
        assertEquals(LONGITUDE, parkedCar.longitude, 0.0)
        assertEquals(ACCURACY_METERS, parkedCar.accuracyMeters)
        assertTrue(parkedCar.isActive)
    }

    @Test
    fun `without a position the manual flow is offered`() = test(
        location = LocationResult.Unavailable(),
    ) { viewModel, _ ->
        viewModel.parkHere()
        advanceUntilIdle()

        val state = viewModel.uiState.value as ParkedCarUiState.CaptureFailed
        assertEquals(ParkedCarCaptureError.Unavailable, state.reason)
    }

    @Test
    fun `a provider that never answers stops at the timeout`() = test(
        location = null,
    ) { viewModel, _ ->
        viewModel.parkHere()
        assertEquals(ParkedCarUiState.Capturing, viewModel.uiState.value)

        advanceUntilIdle()

        val state = viewModel.uiState.value as ParkedCarUiState.CaptureFailed
        assertEquals(ParkedCarCaptureError.Unavailable, state.reason)
    }

    @Test
    fun `a missing permission is reported as such`() = test(
        location = LocationResult.PermissionMissing,
    ) { viewModel, _ ->
        viewModel.parkHere()
        advanceUntilIdle()

        val state = viewModel.uiState.value as ParkedCarUiState.CaptureFailed
        assertEquals(ParkedCarCaptureError.PermissionMissing, state.reason)
    }

    @Test
    fun `a chosen parking is used when the position cannot be measured`() = test(
        location = LocationResult.LocationDisabled,
    ) { viewModel, _ ->
        viewModel.parkHere(PARKING)
        advanceUntilIdle()

        val parkedCar = (viewModel.uiState.value as ParkedCarUiState.Active).parkedCar
        assertEquals(PARKING.latitude, parkedCar.latitude, 0.0)
        assertEquals("way/42", parkedCar.parkingId)
        assertNull(parkedCar.accuracyMeters)
    }

    @Test
    fun `a point marked by hand has no measured accuracy`() = test { viewModel, _ ->
        viewModel.parkAt(LATITUDE, LONGITUDE)
        advanceUntilIdle()

        val parkedCar = (viewModel.uiState.value as ParkedCarUiState.Active).parkedCar
        assertNull(parkedCar.accuracyMeters)
        assertTrue(parkedCar.isAccurate)
    }

    @Test
    fun `ending the parking empties the screen`() = test(
        location = LocationResult.Available(userLocation()),
    ) { viewModel, _ ->
        viewModel.parkHere()
        advanceUntilIdle()

        viewModel.endActive()
        advanceUntilIdle()

        assertEquals(ParkedCarUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `a failed capture does not hide a parking that is still active`() = test(
        location = LocationResult.Available(userLocation()),
    ) { viewModel, provider ->
        viewModel.parkHere()
        advanceUntilIdle()

        provider.result = LocationResult.Unavailable()
        viewModel.parkHere()
        advanceUntilIdle()

        val state = viewModel.uiState.value as ParkedCarUiState.CaptureFailed
        assertEquals(LATITUDE, state.parkedCar?.latitude)

        viewModel.dismissCaptureError()
        assertTrue(viewModel.uiState.value is ParkedCarUiState.Active)
    }

    @Test
    fun `correcting the position does not create a second record`() = test(
        location = LocationResult.Available(userLocation(accuracyMeters = 180f)),
    ) { viewModel, _ ->
        viewModel.parkHere()
        advanceUntilIdle()

        val parkedCar = (viewModel.uiState.value as ParkedCarUiState.Active).parkedCar
        viewModel.moveTo(parkedCar.id, LATITUDE + 0.002, LONGITUDE)
        advanceUntilIdle()

        val corrected = (viewModel.uiState.value as ParkedCarUiState.Active).parkedCar
        assertEquals(parkedCar.id, corrected.id)
        assertEquals(LATITUDE + 0.002, corrected.latitude, 1e-9)
        assertNull(corrected.accuracyMeters)
    }

    @Test
    fun `a write failure is reported instead of crashing`() = test(
        location = LocationResult.Available(userLocation()),
        insertFailure = IllegalStateException("disco cheio"),
    ) { viewModel, _ ->
        viewModel.parkHere()
        advanceUntilIdle()

        val state = viewModel.uiState.value as ParkedCarUiState.CaptureFailed
        assertEquals(ParkedCarCaptureError.Unavailable, state.reason)
    }

    /**
     * O cancelamento nao e uma falha da funcionalidade e nao pode ser convertido
     * num erro apresentado ao utilizador: se fosse apanhado, o ecra anunciaria
     * uma falha de captura sempre que o `viewModelScope` fosse cancelado.
     */
    @Test
    fun `cancellation is not swallowed into a capture error`() = test(
        location = LocationResult.Available(userLocation()),
        insertFailure = CancellationException("cancelado"),
    ) { viewModel, _ ->
        viewModel.parkHere()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value !is ParkedCarUiState.CaptureFailed)
    }

    private fun test(
        location: LocationResult? = LocationResult.Unavailable(),
        insertFailure: Throwable? = null,
        body: suspend kotlinx.coroutines.test.TestScope.(
            ParkedCarViewModel,
            FakeLocationProvider,
        ) -> Unit,
    ) = runTest(dispatcher) {
        val dao = FakeParkedCarDao(insertFailure)
        val provider = FakeLocationProvider(location)
        val viewModel = ParkedCarViewModel(
            repository = ParkedCarRepository(dao) { NOW_MILLIS },
            locationProvider = provider,
        )

        // `stateIn(WhileSubscribed)` so produz estado com alguem a observar.
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }

        body(viewModel, provider)
    }

    /** `result` a `null` representa um fornecedor que nunca responde. */
    private class FakeLocationProvider(var result: LocationResult?) : LocationProvider {
        override suspend fun currentLocation(): LocationResult =
            result ?: awaitCancellation()
    }

    private class FakeParkedCarDao(private val insertFailure: Throwable?) : ParkedCarDao() {

        private val rows = MutableStateFlow<List<ParkedCarEntity>>(emptyList())
        private var nextId = 1L

        override suspend fun insert(parkedCar: ParkedCarEntity): Long {
            insertFailure?.let { throw it }
            val id = nextId++
            rows.value = rows.value + parkedCar.copy(id = id)
            return id
        }

        override fun observeActive(): Flow<ParkedCarEntity?> = rows.map { it.activeRecord() }

        override suspend fun active(): ParkedCarEntity? = rows.value.activeRecord()

        override suspend fun byId(id: Long): ParkedCarEntity? =
            rows.value.firstOrNull { it.id == id }

        override fun observeAll(): Flow<List<ParkedCarEntity>> = rows

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
        const val ACCURACY_METERS = 9f
        const val NOW_MILLIS = 1_700_000_000_000L

        val PARKING = Parking(
            osmId = 42,
            osmType = "way",
            latitude = 38.74,
            longitude = -9.15,
        )

        fun userLocation(accuracyMeters: Float? = ACCURACY_METERS) = UserLocation(
            latitude = LATITUDE,
            longitude = LONGITUDE,
            accuracyMeters = accuracyMeters,
            timestampMillis = NOW_MILLIS,
        )
    }
}
