package pt.vcc.parking.domain.model

/**
 * Caminho pedonal ate ao carro (`vp-09-return-route`).
 *
 * Nao e persistido: ao contrario de [ParkedCar], deixa de valer assim que o
 * utilizador se move, e guardar o trajecto percorrido seria guardar um historico
 * de deslocacoes que a app nao precisa de ter.
 */
data class WalkingRoute(
    val distanceMeters: Double,
    val durationSeconds: Double,
    /** Do ponto de partida ate ao carro; pelo menos dois pontos. */
    val points: List<RoutePoint>,
)

data class RoutePoint(val latitude: Double, val longitude: Double)
