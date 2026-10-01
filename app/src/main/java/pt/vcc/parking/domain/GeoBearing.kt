package pt.vcc.parking.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rumo entre dois pontos, em graus a partir do norte geografico (`vp-09-return-route`).
 *
 * Fica em Kotlin puro pelo mesmo motivo de [GeoDistance]: `Location.bearingTo`
 * pertence ao SDK Android e devolveria zero nos testes unitarios. A formula do
 * rumo inicial ortodromico chega para as distancias a pe desta funcionalidade,
 * onde a diferenca para a loxodromia e inferior ao erro do proprio sensor.
 */
object GeoBearing {

    /** Rumo de partida, em `[0, 360)`, onde 0 e o norte e 90 o este. */
    fun betweenDegrees(
        startLatitude: Double,
        startLongitude: Double,
        endLatitude: Double,
        endLongitude: Double,
    ): Double {
        requireCoordinates(startLatitude, startLongitude)
        requireCoordinates(endLatitude, endLongitude)

        val startLatitudeRadians = Math.toRadians(startLatitude)
        val endLatitudeRadians = Math.toRadians(endLatitude)
        val longitudeDelta = Math.toRadians(endLongitude - startLongitude)

        val y = sin(longitudeDelta) * cos(endLatitudeRadians)
        val x = cos(startLatitudeRadians) * sin(endLatitudeRadians) -
            sin(startLatitudeRadians) * cos(endLatitudeRadians) * cos(longitudeDelta)

        return normalizeDegrees(Math.toDegrees(atan2(y, x)))
    }

    /** Traz qualquer angulo para `[0, 360)`. */
    fun normalizeDegrees(degrees: Double): Double = ((degrees % FULL_TURN) + FULL_TURN) % FULL_TURN

    /**
     * Angulo com sinal entre o rumo e a direcao para onde o dispositivo aponta,
     * em `[-180, 180)`. E este valor que roda a seta no ecra: a seta aponta para
     * o carro e nao para o norte.
     */
    fun relativeDegrees(bearingDegrees: Double, headingDegrees: Double): Double =
        normalizeDegrees(bearingDegrees - headingDegrees + HALF_TURN) - HALF_TURN

    private fun requireCoordinates(latitude: Double, longitude: Double) {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "Latitude fora do intervalo valido"
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "Longitude fora do intervalo valido"
        }
    }

    private const val FULL_TURN = 360.0
    private const val HALF_TURN = 180.0
}
