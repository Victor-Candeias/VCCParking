package pt.vcc.parking.returnroute

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.vcc.parking.VccParkingApplication
import pt.vcc.parking.data.repository.ParkedCarRepository
import pt.vcc.parking.data.repository.ReturnRouteRepository
import pt.vcc.parking.domain.GeoBearing
import pt.vcc.parking.domain.GeoDistance
import pt.vcc.parking.domain.model.ParkedCar
import pt.vcc.parking.domain.model.WalkingRoute
import pt.vcc.parking.location.FusedLocationProvider
import pt.vcc.parking.location.LocationProvider
import pt.vcc.parking.location.LocationResult
import pt.vcc.parking.location.UserLocation

/**
 * Direcao, distancia e rota ate ao carro (`vp-09-return-route`).
 *
 * A bussola e a base e a rota e um extra: todo o estado util — rumo e distancia —
 * e calculado localmente, e o routing so acrescenta a linha no mapa quando
 * responde. E por isso que uma falha de rede nao tem estado de erro proprio.
 *
 * As leituras entram como [Flow] e nao como provedores para que o ecra possa ser
 * testado sem GPS nem sensores, e para que a recolha pare sozinha quando ninguem
 * esta a observar — o que mantem a promessa de so gastar bateria com o ecra a vista.
 */
class ReturnViewModel(
    private val parkedCarRepository: ParkedCarRepository,
    private val routeRepository: ReturnRouteRepository,
    locationUpdates: Flow<UserLocation>,
    headingUpdates: Flow<Float>,
) : ViewModel() {

    private val routeState = MutableStateFlow(RouteState())
    private var routeJob: Job? = null
    private var routedFrom: RouteOrigin? = null

    val uiState: StateFlow<ReturnUiState> = combine(
        parkedCarRepository.observeActive(),
        // O `null` inicial distingue «ainda sem leitura» de «sem carro guardado»;
        // sem ele o ecra ficaria em branco ate o GPS responder.
        locationUpdates.map<UserLocation, UserLocation?> { it }.onStart { emit(null) },
        headingUpdates.map<Float, Float?> { it }.onStart { emit(null) },
        routeState,
    ) { parkedCar, location, heading, route ->
        when {
            parkedCar == null -> ReturnUiState.NoParkedCar
            location == null -> ReturnUiState.Locating
            else -> guiding(parkedCar, location, heading, route)
        }
    }
        // Dentro do fluxo e nao no `init`: assim o pedido de rota acontece
        // apenas enquanto o ecra observa, como o resto da recolha.
        .onEach(::onState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = ReturnUiState.Locating,
        )

    /** «Ja sai»: o regresso terminou e o registo passa para o historico. */
    fun endParking() {
        viewModelScope.launch {
            try {
                parkedCarRepository.endActive()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.w(TAG, "Falha a terminar o estacionamento: ${error.javaClass.simpleName}")
            }
        }
    }

    private fun guiding(
        parkedCar: ParkedCar,
        location: UserLocation,
        heading: Float?,
        route: RouteState,
    ): ReturnUiState.Guiding {
        val distanceMeters = GeoDistance.betweenMeters(
            startLatitude = location.latitude,
            startLongitude = location.longitude,
            endLatitude = parkedCar.latitude,
            endLongitude = parkedCar.longitude,
        )
        val bearingDegrees = GeoBearing.betweenDegrees(
            startLatitude = location.latitude,
            startLongitude = location.longitude,
            endLatitude = parkedCar.latitude,
            endLongitude = parkedCar.longitude,
        )

        // Ja a chegar, a rota desaparece em vez de desenhar uma volta ao
        // quarteirao para percorrer os ultimos metros.
        val arrived = distanceMeters <= ARRIVAL_RADIUS_METERS

        return ReturnUiState.Guiding(
            parkedCar = parkedCar,
            userLocation = location,
            distanceMeters = distanceMeters,
            bearingDegrees = bearingDegrees,
            headingDegrees = heading,
            route = if (arrived) null else route.route,
            routeUnavailable = !arrived && route.attempted && route.route == null,
        )
    }

    private fun onState(state: ReturnUiState) {
        val guiding = state as? ReturnUiState.Guiding ?: return

        if (guiding.arrived) {
            forgetRoute()
            return
        }

        val origin = RouteOrigin(
            latitude = guiding.userLocation.latitude,
            longitude = guiding.userLocation.longitude,
            parkedCarId = guiding.parkedCar.id,
        )

        // Sem este travao, cada rota recebida voltaria a disparar o pedido
        // seguinte: o estado da rota tambem alimenta o `combine`.
        if (!origin.isFarFrom(routedFrom)) return

        routedFrom = origin
        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            val route = routeRepository.walkingRoute(
                startLatitude = origin.latitude,
                startLongitude = origin.longitude,
                endLatitude = guiding.parkedCar.latitude,
                endLongitude = guiding.parkedCar.longitude,
            )
            routeState.value = RouteState(route = route, attempted = true)
        }
    }

    private fun forgetRoute() {
        routeJob?.cancel()
        routeJob = null
        routedFrom = null
        if (routeState.value != RouteState()) routeState.value = RouteState()
    }

    private data class RouteState(
        val route: WalkingRoute? = null,
        /** Distingue «ainda nao se pediu» de «pediu-se e nao ha caminho». */
        val attempted: Boolean = false,
    )

    private data class RouteOrigin(
        val latitude: Double,
        val longitude: Double,
        val parkedCarId: Long,
    ) {
        /** Um desvio curto nao justifica outro pedido ao servico de routing. */
        fun isFarFrom(previous: RouteOrigin?): Boolean {
            if (previous == null || previous.parkedCarId != parkedCarId) return true

            return GeoDistance.betweenMeters(
                startLatitude = previous.latitude,
                startLongitude = previous.longitude,
                endLatitude = latitude,
                endLongitude = longitude,
            ) >= ROUTE_REFRESH_METERS
        }
    }

    companion object {
        private const val TAG = "ReturnViewModel"

        /** Sobrevive a rotacao sem voltar a subscrever sensores e GPS. */
        private const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L

        /**
         * Entre 1 e 5 s a pe, como decidido na fase 1 da spec: em 3 s um peao
         * anda pouco mais de quatro metros, abaixo do erro tipico do GPS urbano.
         */
        const val LOCATION_INTERVAL_MILLIS = 3_000L

        /** Desvio a partir do qual vale a pena pedir uma rota nova. */
        const val ROUTE_REFRESH_METERS = 30.0

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY],
                ) as VccParkingApplication

                val compass = SensorCompassProvider(application)

                ReturnViewModel(
                    parkedCarRepository = application.parkedCarRepository,
                    routeRepository = application.returnRouteRepository,
                    locationUpdates = FusedLocationProvider(application)
                        .locationUpdates(LOCATION_INTERVAL_MILLIS),
                    headingUpdates = compass.headingUpdates(),
                )
            }
        }
    }
}

/**
 * Leituras repetidas da posicao enquanto houver quem observe.
 *
 * Uma leitura falhada nao emite nada: a ultima posicao conhecida continua a ser
 * melhor do que apagar a seta do ecra por causa de um segundo sem sinal.
 */
internal fun LocationProvider.locationUpdates(intervalMillis: Long): Flow<UserLocation> = flow {
    while (true) {
        val result = currentLocation()
        if (result is LocationResult.Available) emit(result.location)
        delay(intervalMillis)
    }
}

/** Sem sensor de rotacao nao ha azimute nenhum para emitir. */
internal fun CompassProvider.headingUpdates(): Flow<Float> =
    if (isAvailable) azimuthDegrees() else emptyFlow()
