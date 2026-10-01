package pt.vcc.parking.ui.details

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.vcc.parking.domain.model.Parking

/** Endereco de edicao do OSM de `vp-12-rich-details`. */
class ContributeToOsmTest {

    @Test
    fun `builds the editor url for a way`() {
        assertEquals(
            "https://www.openstreetmap.org/edit?way=123",
            osmEditUrl(parking(osmType = "way", osmId = 123)),
        )
    }

    @Test
    fun `builds the editor url for a node`() {
        assertEquals(
            "https://www.openstreetmap.org/edit?node=9",
            osmEditUrl(parking(osmType = "node", osmId = 9)),
        )
    }

    @Test
    fun `builds the editor url for a relation`() {
        assertEquals(
            "https://www.openstreetmap.org/edit?relation=4",
            osmEditUrl(parking(osmType = "relation", osmId = 4)),
        )
    }

    @Test
    fun `accepts the type in any case and with spaces`() {
        assertEquals(
            "https://www.openstreetmap.org/edit?way=5",
            osmEditUrl(parking(osmType = " WAY ", osmId = 5)),
        )
    }

    @Test
    fun `falls back to the generic editor for an unknown type`() {
        assertEquals(
            "https://www.openstreetmap.org/edit",
            osmEditUrl(parking(osmType = "area", osmId = 1)),
        )
    }

    private fun parking(osmType: String, osmId: Long): Parking = Parking(
        osmId = osmId,
        osmType = osmType,
        latitude = 38.7,
        longitude = -9.1,
    )
}
