package pt.vcc.parking.returnroute

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pt.vcc.parking.data.local.FakeParkedCarDao
import pt.vcc.parking.data.remote.RouteResult
import pt.vcc.parking.data.remote.RouteService
import pt.vcc.parking.data.repository.ParkedCarRepository
import pt.vcc.parking.data.repository.ReturnRouteRepository
import pt.vcc.parking.domain.model.RoutePoint
import pt.vcc.parking.domain.model.WalkingRoute
import pt.vcc.parking.location.UserLocation

@OptIn(ExperimentalCoroutinesApi::class)
class ReturnViewModelTest {

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
    fun `without a parked car there is nothing to guide to`() = test { fixture ->
        advanceUntilIdle()

        assertEquals(ReturnUiState.NoParkedCar, fixture.viewModel.uiState.value)
    }

    @Test
    fun `waits for the first position before guiding`() = test { fixture ->
        fixture.park()
        advanceUntilIdle()

        assertEquals(ReturnUiState.Locating, fixture.viewModel.uiState.value)
    }

    @Test
    fun `reports the distance and the bearing to the car`() = test { fixture ->
        fixture.park()
        fixture.moveTo(latitude = CAR_LATITUDE - SOUTH_OF_THE_CAR)
        advanceUntilIdle()

        val state = fixture.guiding()
        assertEquals(1112.0, state.distanceMeters, 5.0)
        assertEquals(0.0, state.bearingDegrees, 0.5)
        assertFalse(state.arrived)
    }

    @Test
    fun `without a compass there is no arrow but the distance remains`() = test { fixture ->
        fixture.park()
        fixture.moveTo(latitude = CAR_LATITUDE - SOUTH_OF_THE_CAR)
        advanceUntilIdle()

        val state = fixture.guiding()
        assertNull(state.headingDegrees)
        assertNull(state.relativeBearingDegrees)
        assertTrue(state.distanceMeters > 0.0)
    }

    @Test
    fun `the arrow turns with the device`() = test { fixture ->
        fixture.park()
        fixture.moveTo(latitude = CAR_LATITUDE - SOUTH_OF_THE_CAR)
        fixture.headings.emit(90f)
        advanceUntilIdle()

        val state = fixture.guiding()
        assertEquals(90f, state.headingDegrees)
        assertEquals(-90.0, checkNotNull(state.relativeBearingDegrees), 0.5)
    }

    @Test
    fun `draws the route when the service answers`() = test { fixture ->
        fixture.service.route = ROUTE
        fixture.park()
        fixture.moveTo(latitude = CAR_LATITUDE - SOUTH_OF_THE_CAR)
        advanceUntilIdle()

        val state = fixture.guiding()
        assertNotNull(state.route)
        assertFalse(state.routeUnavailable)
        assertEquals(1, fixture.service.calls)
    }

    /**
     * O ponto central da funcionalidade: sem rede o ecra continua util, porque
     * direcao e distancia sao calculadas no dispositivo.
     */
    @Test
    fun `a routing failure degrades to the compass instead of an error screen`() = test { fixture ->
        fixture.service.result = RouteResult.Failure(emptyList())
        fixture.park()
        fixture.moveTo(latitude = CAR_LATITUDE - SOUTH_OF_THE_CAR)
        advanceUntilIdle()

        val state = fixture.guiding()
        assertNull(state.route)
        assertTrue(state.routeUnavailable)
        assertEquals(0.0, state.bearingDegrees, 0.5)
    }

    @Test
    fun `the warning only appears after the route has been attempted`() = test { fixture ->
        val pending = CompletableDeferred<RouteResult>()
        fixture.service.pending = pending
        fixture.park()
        fixture.moveTo(latitude = CAR_LATITUDE - SOUTH_OF_THE_CAR)
        advanceUntilIdle()

        assertFalse(fixture.guiding().routeUnavailable)

        pending.complete(RouteResult.Empty)
        advanceUntilIdle()

        assertTrue(fixture.guiding().routeUnavailable)
    }

    @Test
    fun `arriving clears the route and announces the car is close`() = test { fixture ->
        fixture.service.route = ROUTE
        fixture.park()
        fixture.moveTo(latitude = CAR_LATITUDE - SOUTH_OF_THE_CAR)
        advanceUntilIdle()
        assertNotNull(fixture.guiding().route)

        fixture.moveTo(latitude = CAR_LATITUDE - NEXT_TO_THE_CAR)
        advanceUntilIdle()

        val state = fixture.guiding()
        assertTrue(state.arrived)
        assertNull(state.route)
        assertFalse(state.routeUnavailable)
    }

    /** Pedir uma rota nova a cada leitura de GPS gastaria rede e bateria a toa. */
    @Test
    fun `a short drift does not ask for a new route`() = test { fixture ->
        fixture.service.route = ROUTE
        fixture.park()
        fixture.moveTo(latitude = CAR_LATITUDE - SOUTH_OF_THE_CAR)
        advanceUntilIdle()

        fixture.moveTo(latitude = CAR_LATITUDE - SOUTH_OF_THE_CAR + SHORT_DRIFT)
        advanceUntilIdle()

        assertEquals(1, fixture.service.calls)
    }

    @Test
    fun `walking far enough asks for a new route`() = test { fixture ->
        fixture.service.route = ROUTE
        fixture.park()
        fixture.moveTo(latitude = CAR_LATITUDE - SOUTH_OF_THE_CAR)
        advanceUntilIdle()

        fixture.moveTo(latitude = CAR_LATITUDE - SOUTH_OF_THE_CAR + LONG_WALK)
        advanceUntilIdle()

        assertEquals(2, fixture.service.calls)
    }

    @Test
    fun `leaving the parking empties the screen`() = test { fixture ->
        fixture.park()
        fixture.moveTo(latitude = CAR_LATITUDE - SOUTH_OF_THE_CAR)
        advanceUntilIdle()

        fixture.viewModel.endParking()
        advanceUntilIdle()

        assertEquals(ReturnUiState.NoParkedCar, fixture.viewModel.uiState.value)
    }

    private fun test(body: suspend TestScope.(Fixture) -> Unit) = runTest(dispatcher) {
        val fixture = Fixture()

        // `stateIn(WhileSubscribed)` so produz estado com alguem a observar.
        backgroundScope.launch(dispatcher) { fixture.viewModel.uiState.collect {} }

        body(fixture)
    }

    private class Fixture {
        val locations = MutableSharedFlow<UserLocation>(replay = 1)
        val headings = MutableSharedFlow<Float>(replay = 1)
        val service = FakeRouteService()

        private val repository = ParkedCarRepository(FakeParkedCarDao()) { NOW_MILLIS }

        val viewModel = ReturnViewModel(
            parkedCarRepository = repository,
            routeRepository = ReturnRouteRepository(service),
            locationUpdates = locations,
            headingUpdates = headings,
        )

        suspend fun park() {
            repository.park(latitude = CAR_LATITUDE, longitude = CAR_LONGITUDE)
        }

        suspend fun moveTo(latitude: Double, longitude: Double = CAR_LONGITUDE) {
            locations.emit(
                UserLocation(
                    latitude = latitude,
                    longitude = longitude,
                    accuracyMeters = 8f,
                    timestampMillis = NOW_MILLIS,
                ),
            )
        }

        fun guiding(): ReturnUiState.Guiding = viewModel.uiState.value as ReturnUiState.Guiding
    }

    /** `pending` representa um servico que ainda nao respondeu. */
    private class FakeRouteService : RouteService {

        var route: WalkingRoute? = null
        var result: RouteResult? = null
        var pending: CompletableDeferred<RouteResult>? = null
        var calls: Int = 0
            private set

        override suspend fun walkingRoute(
            startLatitude: Double,
            startLongitude: Double,
            endLatitude: Double,
            endLongitude: Double,
        ): RouteResult {
            calls++
            pending?.let { return it.await() }

            result?.let { return it }
            return route?.let { RouteResult.Success("https://routing.example", it) }
                ?: RouteResult.Empty
        }
    }

    private companion object {
        const val CAR_LATITUDE = 38.7336
        const val CAR_LONGITUDE = -9.1447
        const val NOW_MILLIS = 1_700_000_000_000L

        /** Cerca de 1112 m a sul do carro. */
        const val SOUTH_OF_THE_CAR = 0.01

        /** Cerca de 33 m, dentro do raio de chegada. */
        const val NEXT_TO_THE_CAR = 0.0003

        /** Cerca de 11 m, abaixo do limiar de nova rota. */
        const val SHORT_DRIFT = 0.0001

        /** Cerca de 56 m, acima do limiar de nova rota. */
        const val LONG_WALK = 0.0005

        val ROUTE = WalkingRoute(
            distanceMeters = 1200.0,
            durationSeconds = 900.0,
            points = listOf(
                RoutePoint(latitude = 38.7236, longitude = -9.1447),
                RoutePoint(latitude = CAR_LATITUDE, longitude = CAR_LONGITUDE),
            ),
        )
    }
}
