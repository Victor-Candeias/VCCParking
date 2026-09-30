package pt.vcc.parking.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverpassDtoTest {

    @Test
    fun `parses nodes ways and relations`() {
        val json = """
            {
              "version": 0.6,
              "generator": "Overpass API",
              "osm3s": { "timestamp_osm_base": "2026-09-30T00:00:00Z" },
              "elements": [
                {
                  "type": "node",
                  "id": 1,
                  "lat": 38.7253,
                  "lon": -9.15,
                  "tags": { "amenity": "parking", "name": "Parque Node" }
                },
                {
                  "type": "way",
                  "id": 2,
                  "center": { "lat": 38.73, "lon": -9.16 },
                  "tags": { "amenity": "parking", "capacity": "120" }
                },
                {
                  "type": "relation",
                  "id": 3,
                  "center": { "lat": 38.74, "lon": -9.17 },
                  "tags": { "amenity": "parking", "access": "private" }
                }
              ]
            }
        """.trimIndent()

        val response = overpassJson.decodeFromString(OverpassResponse.serializer(), json)

        assertEquals(3, response.elements.size)

        val node = response.elements[0]
        assertEquals("node", node.type)
        assertEquals(38.7253, node.latitude!!, 1e-9)
        assertEquals(-9.15, node.longitude!!, 1e-9)
        assertEquals("Parque Node", node.tags["name"])

        val way = response.elements[1]
        assertEquals(38.73, way.latitude!!, 1e-9)
        assertEquals(-9.16, way.longitude!!, 1e-9)

        val relation = response.elements[2]
        assertEquals(38.74, relation.latitude!!, 1e-9)
        assertEquals("private", relation.tags["access"])

        assertTrue(response.elements.all { it.hasCoordinates })
    }

    @Test
    fun `tolerates missing tags and missing coordinates`() {
        val json = """
            { "elements": [ { "type": "way", "id": 7 } ] }
        """.trimIndent()

        val response = overpassJson.decodeFromString(OverpassResponse.serializer(), json)
        val element = response.elements.single()

        assertTrue(element.tags.isEmpty())
        assertNull(element.latitude)
        assertNull(element.longitude)
        assertFalse(element.hasCoordinates)
    }

    @Test
    fun `tolerates a response without elements`() {
        val response = overpassJson.decodeFromString(OverpassResponse.serializer(), "{}")

        assertTrue(response.elements.isEmpty())
    }
}
