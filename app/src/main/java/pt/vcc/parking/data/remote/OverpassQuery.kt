package pt.vcc.parking.data.remote

import java.util.Locale

/**
 * Constroi a query Overpass usada para procurar estacionamento.
 *
 * Segue a seccao 15 do documento do MVP: `nwr` abrange node, way e relation e
 * `out center tags` traz o centroide sem o custo da geometria completa.
 */
object OverpassQuery {

    const val DEFAULT_RADIUS_METERS = 1_000
    const val MIN_RADIUS_METERS = 100
    const val MAX_RADIUS_METERS = 50_000
    const val SERVER_TIMEOUT_SECONDS = 15

    fun buildParkingQuery(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = DEFAULT_RADIUS_METERS,
    ): String {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "Latitude fora do intervalo valido"
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "Longitude fora do intervalo valido"
        }

        val radius = radiusMeters.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)

        return "[out:json][timeout:$SERVER_TIMEOUT_SECONDS];" +
            "nwr[\"amenity\"=\"parking\"]" +
            "(around:$radius,${latitude.asCoordinate()},${longitude.asCoordinate()});" +
            "out center tags;"
    }

    // A query e texto: uma locale com virgula decimal produziria HTTP 400.
    private fun Double.asCoordinate(): String = String.format(Locale.ROOT, "%.6f", this)
}
