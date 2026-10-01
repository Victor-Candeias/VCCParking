package pt.vcc.parking.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GeoBearingTest {

    @Test
    fun `points to the north when the destination is straight up`() {
        assertEquals(0.0, bearingTo(latitude = 1.0, longitude = 0.0), TOLERANCE)
    }

    @Test
    fun `points to the east when the destination is to the right`() {
        assertEquals(90.0, bearingTo(latitude = 0.0, longitude = 1.0), TOLERANCE)
    }

    @Test
    fun `points to the south when the destination is straight down`() {
        assertEquals(180.0, bearingTo(latitude = -1.0, longitude = 0.0), TOLERANCE)
    }

    @Test
    fun `points to the west when the destination is to the left`() {
        assertEquals(270.0, bearingTo(latitude = 0.0, longitude = -1.0), TOLERANCE)
    }

    /** Ponto conhecido: do Marques de Pombal para o Parque das Nacoes, a nordeste. */
    @Test
    fun `matches a known bearing between two points in Lisbon`() {
        val bearing = GeoBearing.betweenDegrees(
            startLatitude = 38.7253,
            startLongitude = -9.1500,
            endLatitude = 38.7680,
            endLongitude = -9.0950,
        )

        assertEquals(45.11, bearing, 0.05)
    }

    @Test
    fun `the bearing to the same point is well defined`() {
        assertEquals(0.0, bearingTo(latitude = 0.0, longitude = 0.0), TOLERANCE)
    }

    @Test
    fun `every bearing lands inside a single turn`() {
        assertEquals(10.0, GeoBearing.normalizeDegrees(370.0), TOLERANCE)
        assertEquals(350.0, GeoBearing.normalizeDegrees(-10.0), TOLERANCE)
        assertEquals(0.0, GeoBearing.normalizeDegrees(720.0), TOLERANCE)
    }

    /**
     * O caso que estraga uma bussola mal feita: entre 350 e 10 graus a diferenca
     * sao 20 graus para a direita, nao 340 para a esquerda.
     */
    @Test
    fun `the relative angle takes the short way around north`() {
        assertEquals(20.0, GeoBearing.relativeDegrees(10.0, 350.0), TOLERANCE)
        assertEquals(-20.0, GeoBearing.relativeDegrees(350.0, 10.0), TOLERANCE)
    }

    @Test
    fun `the relative angle stays inside half a turn`() {
        assertEquals(-180.0, GeoBearing.relativeDegrees(180.0, 0.0), TOLERANCE)
        assertEquals(90.0, GeoBearing.relativeDegrees(90.0, 0.0), TOLERANCE)
        assertEquals(0.0, GeoBearing.relativeDegrees(45.0, 45.0), TOLERANCE)
    }

    @Test
    fun `rejects coordinates outside the valid range`() {
        assertThrows(IllegalArgumentException::class.java) {
            GeoBearing.betweenDegrees(91.0, 0.0, 0.0, 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GeoBearing.betweenDegrees(0.0, 0.0, 0.0, 181.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GeoBearing.betweenDegrees(0.0, 0.0, Double.NaN, 0.0)
        }
    }

    private fun bearingTo(latitude: Double, longitude: Double): Double =
        GeoBearing.betweenDegrees(
            startLatitude = 0.0,
            startLongitude = 0.0,
            endLatitude = latitude,
            endLongitude = longitude,
        )

    private companion object {
        const val TOLERANCE = 1e-6
    }
}
