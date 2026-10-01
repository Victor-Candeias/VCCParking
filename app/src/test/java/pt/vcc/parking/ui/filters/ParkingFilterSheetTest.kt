package pt.vcc.parking.ui.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Leitura da altura do veiculo escrita a mao (`vp-12-rich-details`). */
class ParkingFilterSheetTest {

    @Test
    fun `reads a height written with a dot`() {
        assertEquals(1.85, "1.85".toHeightMeters()!!, TOLERANCE)
    }

    @Test
    fun `reads a height written with a comma`() {
        assertEquals(1.85, "1,85".toHeightMeters()!!, TOLERANCE)
    }

    @Test
    fun `ignores surrounding spaces`() {
        assertEquals(2.0, "  2 ".toHeightMeters()!!, TOLERANCE)
    }

    @Test
    fun `an empty field means no height filter`() {
        assertNull("".toHeightMeters())
        assertNull("   ".toHeightMeters())
    }

    @Test
    fun `text that is not a number means no height filter`() {
        assertNull("alto".toHeightMeters())
        assertNull("1,8,5".toHeightMeters())
    }

    @Test
    fun `zero and negative heights are treated as no filter`() {
        assertNull("0".toHeightMeters())
        assertNull("-1.5".toHeightMeters())
    }

    private companion object {
        const val TOLERANCE = 0.0001
    }
}
