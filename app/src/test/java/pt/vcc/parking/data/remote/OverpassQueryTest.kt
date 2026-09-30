package pt.vcc.parking.data.remote

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OverpassQueryTest {

    private val defaultLocale: Locale = Locale.getDefault()

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun `builds the query documented in the MVP`() {
        val query = OverpassQuery.buildParkingQuery(38.7253, -9.1500, 1000)

        assertEquals(
            "[out:json][timeout:15];" +
                "nwr[\"amenity\"=\"parking\"]" +
                "(around:1000,38.725300,-9.150000);" +
                "out center tags;",
            query,
        )
    }

    @Test
    fun `uses a decimal point regardless of the system locale`() {
        Locale.setDefault(Locale.GERMANY)

        val query = OverpassQuery.buildParkingQuery(38.7253, -9.1500, 1000)

        assertTrue(query, query.contains("around:1000,38.725300,-9.150000"))
    }

    @Test
    fun `clamps the radius to the accepted range`() {
        val tooSmall = OverpassQuery.buildParkingQuery(0.0, 0.0, 1)
        val tooLarge = OverpassQuery.buildParkingQuery(0.0, 0.0, 999_999)

        assertTrue(tooSmall, tooSmall.contains("around:${OverpassQuery.MIN_RADIUS_METERS},"))
        assertTrue(tooLarge, tooLarge.contains("around:${OverpassQuery.MAX_RADIUS_METERS},"))
    }

    @Test
    fun `uses one kilometre by default`() {
        val query = OverpassQuery.buildParkingQuery(38.7253, -9.1500)

        assertTrue(query, query.contains("around:1000,"))
    }

    @Test
    fun `rejects coordinates outside the valid range`() {
        assertThrows(IllegalArgumentException::class.java) {
            OverpassQuery.buildParkingQuery(91.0, 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OverpassQuery.buildParkingQuery(0.0, 181.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OverpassQuery.buildParkingQuery(Double.NaN, 0.0)
        }
    }
}
