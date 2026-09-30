package pt.vcc.parking.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.vcc.parking.domain.model.Parking
import pt.vcc.parking.location.UserLocation

class ParkingFiltersTest {

    private fun parking(
        id: Long,
        latitude: Double = 38.7253,
        longitude: Double = -9.15,
        access: String? = null,
    ) = Parking(
        osmId = id,
        osmType = "node",
        latitude = latitude,
        longitude = longitude,
        access = access,
    )

    @Test
    fun `excludes only explicitly private parking`() {
        val parking = listOf(
            parking(1, access = "private"),
            parking(2, access = "customers"),
            parking(3, access = null),
            parking(4, access = "yes"),
        )

        val visible = parking.excludePrivate()

        assertEquals(listOf(2L, 3L, 4L), visible.map { it.osmId })
    }

    @Test
    fun `ignores the case and the spacing of the access tag`() {
        val parking = listOf(parking(1, access = " Private "))

        assertTrue(parking.excludePrivate().isEmpty())
    }

    @Test
    fun `fills in the distance from the user position`() {
        val parking = listOf(parking(1, latitude = 38.7336, longitude = -9.1450))

        val withDistance = parking.withDistanceFrom(38.7253, -9.1500).single()

        assertEquals(
            GeoDistance.betweenMeters(38.7253, -9.1500, 38.7336, -9.1450),
            withDistance.distanceMeters!!,
            1e-9,
        )
    }

    @Test
    fun `sorts by increasing distance and keeps the unknown ones last`() {
        val parking = listOf(
            parking(1).copy(distanceMeters = 900.0),
            parking(2).copy(distanceMeters = null),
            parking(3).copy(distanceMeters = 120.0),
        )

        val sorted = parking.sortedByDistance()

        assertEquals(listOf(3L, 1L, 2L), sorted.map { it.osmId })
        assertNull(sorted.last().distanceMeters)
    }

    @Test
    fun `follows the order of the complete flow`() {
        val parking = listOf(
            parking(1, latitude = 38.7500, longitude = -9.1500),
            parking(2, latitude = 38.7260, longitude = -9.1500, access = "private"),
            parking(3, latitude = 38.7300, longitude = -9.1500),
        )

        val nearest = parking.nearestFrom(38.7253, -9.1500)

        assertEquals(listOf(3L, 1L), nearest.map { it.osmId })
        assertTrue(nearest.all { it.distanceMeters != null })
        assertTrue(nearest[0].distanceMeters!! < nearest[1].distanceMeters!!)
    }

    @Test
    fun `accepts the user location of the location layer`() {
        val location = UserLocation(
            latitude = 38.7253,
            longitude = -9.1500,
            accuracyMeters = 10f,
            timestampMillis = 0L,
        )
        val parking = listOf(parking(1, latitude = 38.7300, longitude = -9.1500))

        val nearest = parking.nearestFrom(location)

        assertEquals(
            parking.nearestFrom(38.7253, -9.1500).single().distanceMeters!!,
            nearest.single().distanceMeters!!,
            1e-9,
        )
    }

    @Test
    fun `returns an empty list when everything is private`() {
        val parking = listOf(parking(1, access = "private"))

        assertTrue(parking.nearestFrom(38.7253, -9.1500).isEmpty())
    }
}
