package pt.vcc.parking.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pt.vcc.parking.domain.model.RoutePoint
import pt.vcc.parking.domain.model.WalkingRoute

/**
 * Resposta do OSRM com geometria GeoJSON (`vp-09-return-route`).
 *
 * Todos os campos tem omissao: uma instancia publica que devolva menos do que o
 * esperado deve degradar para o modo bussola, nao rebentar a desserializar.
 */
@Serializable
data class RouteResponse(
    val code: String = "",
    val routes: List<RouteDto> = emptyList(),
)

@Serializable
data class RouteDto(
    val distance: Double = 0.0,
    val duration: Double = 0.0,
    val geometry: RouteGeometry? = null,
)

@Serializable
data class RouteGeometry(
    /** GeoJSON: cada par e `[longitude, latitude]`, por esta ordem. */
    val coordinates: List<List<Double>> = emptyList(),
)

/** `Ok` e o unico codigo do OSRM que traz rota utilizavel. */
private const val OSRM_OK = "Ok"

/** Uma linha precisa de dois pontos; com menos nao ha nada para desenhar. */
private const val MINIMUM_POINTS = 2

/**
 * Converte a resposta na rota de dominio, ou `null` quando nao ha caminho.
 *
 * Um par de coordenadas invalido e descartado em vez de invalidar a rota
 * inteira: um ponto a mais ou a menos numa linha de centenas nao muda o que o
 * utilizador ve, mas perder a rota toda manda-o de volta para a bussola.
 */
internal fun RouteResponse.toWalkingRoute(): WalkingRoute? {
    if (!code.equals(OSRM_OK, ignoreCase = true)) return null

    val route = routes.firstOrNull() ?: return null
    val points = route.geometry?.coordinates.orEmpty().mapNotNull { it.toRoutePoint() }
    if (points.size < MINIMUM_POINTS) return null

    return WalkingRoute(
        distanceMeters = route.distance,
        durationSeconds = route.duration,
        points = points,
    )
}

private fun List<Double>.toRoutePoint(): RoutePoint? {
    if (size < 2) return null

    val longitude = this[0]
    val latitude = this[1]
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null

    return RoutePoint(latitude = latitude, longitude = longitude)
}

internal val routeJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
