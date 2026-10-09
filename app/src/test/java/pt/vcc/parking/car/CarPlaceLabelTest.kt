package pt.vcc.parking.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O limite de linhas e a etiqueta do marcador sao as duas regras da lista
 * projetada que o host impoe e a app tem de respeitar sem rebentar.
 */
class CarPlaceLabelTest {

    @Test
    fun `the marker label starts at one`() {
        assertEquals("1", CarPlaceLabel.markerLabel(0))
        assertEquals("2", CarPlaceLabel.markerLabel(1))
    }

    @Test
    fun `the marker label never exceeds the allowed length`() {
        val label = CarPlaceLabel.markerLabel(99_998)

        assertEquals(CarPlaceLabel.MAX_MARKER_LABEL_LENGTH, label.length)
        assertTrue(label.all { it.isDigit() })
    }

    @Test
    fun `the list is cut to the limit announced by the host`() {
        val places = (1..10).toList()

        assertEquals(listOf(1, 2, 3), CarPlaceLabel.limitedTo(places, contentLimit = 3))
    }

    @Test
    fun `a limit above the list size keeps every place`() {
        val places = listOf(1, 2)

        assertEquals(places, CarPlaceLabel.limitedTo(places, contentLimit = 6))
    }

    @Test
    fun `an invalid limit still shows something`() {
        val places = listOf(1, 2, 3)

        assertEquals(listOf(1), CarPlaceLabel.limitedTo(places, contentLimit = 0))
        assertEquals(listOf(1), CarPlaceLabel.limitedTo(places, contentLimit = -5))
    }

    @Test
    fun `an empty list survives any limit`() {
        assertTrue(CarPlaceLabel.limitedTo(emptyList<Int>(), contentLimit = 6).isEmpty())
        assertTrue(CarPlaceLabel.limitedTo(emptyList<Int>(), contentLimit = 0).isEmpty())
    }

    @Test
    fun `a huge host limit is capped so the template fits in the binder`() {
        val places = (1..280).toList()

        val visible = CarPlaceLabel.placesFor(places, hostLimit = 1000)

        assertEquals(CarPlaceLabel.MAX_PLACES, visible.size)
        assertEquals(places.take(CarPlaceLabel.MAX_PLACES), visible)
    }

    @Test
    fun `a host limit below the cap is still respected`() {
        val places = (1..280).toList()

        assertEquals(listOf(1, 2, 3, 4, 5, 6), CarPlaceLabel.placesFor(places, hostLimit = 6))
    }

    @Test
    fun `the capped list still shows something with an invalid host limit`() {
        assertEquals(listOf(1), CarPlaceLabel.placesFor(listOf(1, 2, 3), hostLimit = 0))
    }
}
