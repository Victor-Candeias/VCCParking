package pt.vcc.parking.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Distancia entre dois pontos, em metros.
 *
 * A seccao 21 do documento do MVP sugere `Location.distanceBetween`, mas essa API
 * pertence ao SDK Android e devolveria zero nos testes unitarios. O calculo fica em
 * Kotlin puro pelo mesmo motivo que levou `vp-02-location` a criar `UserLocation`:
 * o dominio nao depende do SDK. Para os raios da seccao 20 o erro da formula
 * esferica face ao elipsoide WGS84 fica abaixo de 0,5%.
 */
object GeoDistance {

    /** Raio medio da Terra segundo o WGS84. */
    private const val EARTH_RADIUS_METERS = 6_371_008.8

    fun betweenMeters(
        startLatitude: Double,
        startLongitude: Double,
        endLatitude: Double,
        endLongitude: Double,
    ): Double {
        requireCoordinates(startLatitude, startLongitude)
        requireCoordinates(endLatitude, endLongitude)

        val startLatitudeRadians = Math.toRadians(startLatitude)
        val endLatitudeRadians = Math.toRadians(endLatitude)
        val latitudeDelta = endLatitudeRadians - startLatitudeRadians
        val longitudeDelta = Math.toRadians(endLongitude - startLongitude)

        val halfChordSquared = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(startLatitudeRadians) * cos(endLatitudeRadians) *
            sin(longitudeDelta / 2) * sin(longitudeDelta / 2)

        // O min protege contra sqrt > 1 por erro de virgula flutuante em pontos opostos.
        return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(halfChordSquared)))
    }

    private fun requireCoordinates(latitude: Double, longitude: Double) {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "Latitude fora do intervalo valido"
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "Longitude fora do intervalo valido"
        }
    }
}
