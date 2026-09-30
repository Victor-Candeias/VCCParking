package pt.vcc.parking.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingMapperTest {

    @Test
    fun `maps every tag of the internal model`() {
        val element = OverpassElement(
            type = "node",
            id = 1,
            lat = 38.7253,
            lon = -9.15,
            tags = mapOf(
                "amenity" to "parking",
                "name" to "Parque Saldanha",
                "parking" to "underground",
                "capacity" to "120",
                "operator" to "EMEL",
                "access" to "yes",
                "fee" to "yes",
                "opening_hours" to "24/7",
                "capacity:disabled" to "4",
                "zone" to "verde",
                "zone:colour" to "#00A000",
                "phone" to "+351 210 000 000",
                "website" to "https://example.org",
            ),
        )

        val parking = element.toParking()!!

        assertEquals(1L, parking.osmId)
        assertEquals("node", parking.osmType)
        assertEquals("node/1", parking.id)
        assertEquals(38.7253, parking.latitude, 1e-9)
        assertEquals(-9.15, parking.longitude, 1e-9)
        assertEquals("Parque Saldanha", parking.name)
        assertEquals("underground", parking.parkingType)
        assertEquals(120, parking.capacity)
        assertEquals("EMEL", parking.operator)
        assertEquals("yes", parking.access)
        assertEquals("yes", parking.fee)
        assertEquals("24/7", parking.openingHours)
        assertEquals(4, parking.disabledCapacity)
        assertEquals("verde", parking.zone)
        assertEquals("#00A000", parking.zoneColour)
        assertEquals("+351 210 000 000", parking.phone)
        assertEquals("https://example.org", parking.website)
        assertNull(parking.distanceMeters)
    }

    @Test
    fun `uses the centre of ways and relations`() {
        val way = OverpassElement(
            type = "way",
            id = 2,
            center = OverpassCenter(lat = 38.73, lon = -9.16),
        )
        val relation = OverpassElement(
            type = "relation",
            id = 3,
            center = OverpassCenter(lat = 38.74, lon = -9.17),
        )

        val mapped = listOf(way, relation).toParking()

        assertEquals(2, mapped.size)
        assertEquals(38.73, mapped[0].latitude, 1e-9)
        assertEquals(-9.16, mapped[0].longitude, 1e-9)
        assertEquals("relation/3", mapped[1].id)
    }

    @Test
    fun `keeps working when tags are missing`() {
        val element = OverpassElement(type = "node", id = 4, lat = 38.7, lon = -9.1)

        val parking = element.toParking()

        assertNotNull(parking)
        assertNull(parking!!.name)
        assertNull(parking.capacity)
        assertNull(parking.fee)
    }

    @Test
    fun `treats blank and non numeric tags as missing information`() {
        val element = OverpassElement(
            type = "node",
            id = 5,
            lat = 38.7,
            lon = -9.1,
            tags = mapOf(
                "name" to "   ",
                "capacity" to "yes",
                "capacity:disabled" to "-3",
            ),
        )

        val parking = element.toParking()!!

        assertNull(parking.name)
        assertNull(parking.capacity)
        assertNull(parking.disabledCapacity)
    }

    @Test
    fun `trims tag values`() {
        val element = OverpassElement(
            type = "node",
            id = 6,
            lat = 38.7,
            lon = -9.1,
            tags = mapOf("name" to "  Parque  ", "capacity" to " 30 "),
        )

        val parking = element.toParking()!!

        assertEquals("Parque", parking.name)
        assertEquals(30, parking.capacity)
    }

    @Test
    fun `falls back to the contact tags`() {
        val element = OverpassElement(
            type = "node",
            id = 7,
            lat = 38.7,
            lon = -9.1,
            tags = mapOf(
                "contact:phone" to "+351 220 000 000",
                "contact:website" to "https://contact.example.org",
            ),
        )

        val parking = element.toParking()!!

        assertEquals("+351 220 000 000", parking.phone)
        assertEquals("https://contact.example.org", parking.website)
    }

    @Test
    fun `discards elements without usable coordinates`() {
        val withoutCoordinates = OverpassElement(type = "way", id = 8)
        val outOfRange = OverpassElement(type = "node", id = 9, lat = 91.0, lon = 0.0)
        val notFinite = OverpassElement(type = "node", id = 10, lat = 0.0, lon = Double.NaN)

        assertNull(withoutCoordinates.toParking())
        assertNull(outOfRange.toParking())
        assertNull(notFinite.toParking())
        assertTrue(listOf(withoutCoordinates, outOfRange, notFinite).toParking().isEmpty())
    }
}
