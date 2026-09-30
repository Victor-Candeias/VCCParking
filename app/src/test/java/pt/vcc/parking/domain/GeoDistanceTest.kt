package pt.vcc.parking.domain

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoDistanceTest {

    @Test
    fun `returns zero for the same point`() {
        val distance = GeoDistance.betweenMeters(38.7253, -9.15, 38.7253, -9.15)

        assertEquals(0.0, distance, 1e-9)
    }

    @Test
    fun `matches a known short distance`() {
        // Marques de Pombal para Saldanha: cerca de 1020 m em linha reta.
        val distance = GeoDistance.betweenMeters(38.7253, -9.1500, 38.7336, -9.1450)

        assertTrue("$distance", abs(distance - 1020.0) < 1020.0 * 0.01)
    }

    @Test
    fun `matches a known long distance`() {
        // Lisboa para Porto: cerca de 274 km em linha reta.
        val distance = GeoDistance.betweenMeters(38.7223, -9.1393, 41.1579, -8.6291)

        assertTrue("$distance", abs(distance - 274_000.0) < 274_000.0 * 0.005)
    }

    @Test
    fun `is symmetric`() {
        val forward = GeoDistance.betweenMeters(38.7223, -9.1393, 41.1579, -8.6291)
        val backward = GeoDistance.betweenMeters(41.1579, -8.6291, 38.7223, -9.1393)

        assertEquals(forward, backward, 1e-6)
    }

    @Test
    fun `handles opposite points without overflowing the arc sine`() {
        val distance = GeoDistance.betweenMeters(0.0, 0.0, 0.0, 180.0)

        assertTrue("$distance", distance.isFinite() && distance > 20_000_000.0)
    }

    @Test
    fun `rejects coordinates outside the valid range`() {
        assertThrows(IllegalArgumentException::class.java) {
            GeoDistance.betweenMeters(91.0, 0.0, 0.0, 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GeoDistance.betweenMeters(0.0, 0.0, 0.0, 181.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GeoDistance.betweenMeters(Double.NaN, 0.0, 0.0, 0.0)
        }
    }
}
