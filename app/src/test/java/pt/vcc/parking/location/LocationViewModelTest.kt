package pt.vcc.parking.location

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts idle`() {
        val viewModel = LocationViewModel(FakeLocationProvider(LocationResult.Unavailable()))

        assertEquals(LocationUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `available result exposes location`() = runTest(dispatcher) {
        val location = UserLocation(38.7253, -9.1500, 12f, 1_000L)
        val viewModel = LocationViewModel(FakeLocationProvider(LocationResult.Available(location)))

        viewModel.refreshLocation()
        advanceUntilIdle()

        assertEquals(LocationUiState.Available(location), viewModel.uiState.value)
    }

    @Test
    fun `missing permission maps to permission required`() = runTest(dispatcher) {
        val viewModel = LocationViewModel(FakeLocationProvider(LocationResult.PermissionMissing))

        viewModel.refreshLocation()
        advanceUntilIdle()

        assertEquals(LocationUiState.PermissionRequired, viewModel.uiState.value)
    }

    @Test
    fun `disabled location maps to location disabled`() = runTest(dispatcher) {
        val viewModel = LocationViewModel(FakeLocationProvider(LocationResult.LocationDisabled))

        viewModel.refreshLocation()
        advanceUntilIdle()

        assertEquals(LocationUiState.LocationDisabled, viewModel.uiState.value)
    }

    @Test
    fun `failure maps to unavailable`() = runTest(dispatcher) {
        val viewModel = LocationViewModel(
            FakeLocationProvider(LocationResult.Unavailable(IllegalStateException("boom"))),
        )

        viewModel.refreshLocation()
        advanceUntilIdle()

        assertEquals(LocationUiState.Unavailable, viewModel.uiState.value)
    }

    @Test
    fun `permanent denial is exposed without calling the provider`() = runTest(dispatcher) {
        val provider = FakeLocationProvider(LocationResult.PermissionMissing)
        val viewModel = LocationViewModel(provider)

        viewModel.onPermissionPermanentlyDenied()
        advanceUntilIdle()

        assertEquals(LocationUiState.PermissionDenied, viewModel.uiState.value)
        assertEquals(0, provider.calls)
    }

    @Test
    fun `concurrent refresh does not call the provider twice`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val provider = FakeLocationProvider(LocationResult.PermissionMissing, gate)
        val viewModel = LocationViewModel(provider)

        viewModel.refreshLocation()
        advanceUntilIdle()
        viewModel.refreshLocation()
        advanceUntilIdle()

        assertEquals(LocationUiState.Loading, viewModel.uiState.value)
        assertEquals(1, provider.calls)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(LocationUiState.PermissionRequired, viewModel.uiState.value)
    }

    private class FakeLocationProvider(
        private val result: LocationResult,
        private val gate: CompletableDeferred<Unit>? = null,
    ) : LocationProvider {

        var calls = 0
            private set

        override suspend fun currentLocation(): LocationResult {
            calls++
            gate?.await()
            return result
        }
    }
}
