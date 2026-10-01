package pt.vcc.parking.domain

import java.time.LocalDateTime
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
        fee: String? = null,
        covered: Boolean? = null,
        disabledCapacity: Int? = null,
        chargingCapacity: Int? = null,
        maxHeightMeters: Double? = null,
        openingHours: String? = null,
    ) = Parking(
        osmId = id,
        osmType = "node",
        latitude = latitude,
        longitude = longitude,
        access = access,
        fee = fee,
        covered = covered,
        disabledCapacity = disabledCapacity,
        chargingCapacity = chargingCapacity,
        maxHeightMeters = maxHeightMeters,
        openingHours = openingHours,
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

    // vp-12-rich-details: a regra que todos os filtros partilham e que a falta
    // de informacao nunca exclui um parque.

    @Test
    fun `an inactive filter lets everything through untouched`() {
        val parking = listOf(parking(1), parking(2))

        val result = parking.applyFilter(ParkingFilter(), NOW)

        assertEquals(parking, result.matching)
        assertTrue(result.unknown.isEmpty())
    }

    @Test
    fun `the free filter never excludes parking without a fee tag`() {
        val parking = listOf(
            parking(1, fee = "no"),
            parking(2, fee = "yes"),
            parking(3, fee = null),
        )

        val result = parking.applyFilter(ParkingFilter(freeOnly = true), NOW)

        assertEquals(listOf(1L), result.matching.map { it.osmId })
        // O parque sem tag nao desaparece: vai para a seccao «sem informacao».
        assertEquals(listOf(3L), result.unknown.map { it.osmId })
    }

    @Test
    fun `the covered filter separates unknown from explicitly uncovered`() {
        val parking = listOf(
            parking(1, covered = true),
            parking(2, covered = false),
            parking(3, covered = null),
        )

        val result = parking.applyFilter(ParkingFilter(coveredOnly = true), NOW)

        assertEquals(listOf(1L), result.matching.map { it.osmId })
        assertEquals(listOf(3L), result.unknown.map { it.osmId })
    }

    @Test
    fun `zero disabled spaces is an answer and not missing information`() {
        val parking = listOf(
            parking(1, disabledCapacity = 4),
            parking(2, disabledCapacity = 0),
            parking(3, disabledCapacity = null),
        )

        val result = parking.applyFilter(ParkingFilter(disabledSpacesOnly = true), NOW)

        assertEquals(listOf(1L), result.matching.map { it.osmId })
        assertEquals(listOf(3L), result.unknown.map { it.osmId })
    }

    @Test
    fun `the charging filter follows the same rule`() {
        val parking = listOf(
            parking(1, chargingCapacity = 2),
            parking(2, chargingCapacity = 0),
            parking(3, chargingCapacity = null),
        )

        val result = parking.applyFilter(ParkingFilter(chargingSpacesOnly = true), NOW)

        assertEquals(listOf(1L), result.matching.map { it.osmId })
        assertEquals(listOf(3L), result.unknown.map { it.osmId })
    }

    @Test
    fun `the open now filter treats unsupported syntax as missing information`() {
        val parking = listOf(
            parking(1, openingHours = "24/7"),
            parking(2, openingHours = "Mo-Fr 08:00-20:00"),
            parking(3, openingHours = "Mo-Fr 08:00-20:00; PH off"),
            parking(4, openingHours = null),
        )

        // Quarta-feira as 21:00: o parque 2 esta fechado, o 3 e o 4 sao incertos.
        val result = parking.applyFilter(
            ParkingFilter(openNowOnly = true),
            NOW.withHour(21),
        )

        assertEquals(listOf(1L), result.matching.map { it.osmId })
        assertEquals(listOf(3L, 4L), result.unknown.map { it.osmId })
    }

    @Test
    fun `height only excludes parking that declares a lower limit`() {
        val parking = listOf(
            parking(1, maxHeightMeters = 2.5),
            parking(2, maxHeightMeters = 1.8),
            parking(3, maxHeightMeters = null),
        )

        val result = parking.applyFilter(ParkingFilter(vehicleHeightMeters = 2.1), NOW)

        // Sem limite declarado o carro passa ate prova em contrario; exigir a
        // tag esvaziaria a lista na maioria das zonas.
        assertEquals(listOf(1L, 3L), result.matching.map { it.osmId })
        assertTrue(result.unknown.isEmpty())
    }

    @Test
    fun `a single missing criterion makes the whole parking uncertain`() {
        val parking = listOf(parking(1, fee = "no", covered = null))

        val result = parking.applyFilter(
            ParkingFilter(freeOnly = true, coveredOnly = true),
            NOW,
        )

        assertTrue(result.matching.isEmpty())
        assertEquals(listOf(1L), result.unknown.map { it.osmId })
    }

    @Test
    fun `an explicit failure beats a missing criterion`() {
        // Sabe-se que e pago: e excluido, nao fica incerto por lhe faltar a
        // cobertura.
        val parking = listOf(parking(1, fee = "yes", covered = null))

        val result = parking.applyFilter(
            ParkingFilter(freeOnly = true, coveredOnly = true),
            NOW,
        )

        assertTrue(result.isEmpty)
    }

    @Test
    fun `the active count reflects the filters that are on`() {
        assertEquals(0, ParkingFilter().activeCount)
        assertEquals(
            2,
            ParkingFilter(freeOnly = true, vehicleHeightMeters = 2.0).activeCount,
        )
        assertTrue(ParkingFilter(openNowOnly = true).isActive)
        assertTrue(!ParkingFilter(openNowOnly = true).cleared().isActive)
    }

    private companion object {
        /** 2026-01-07 foi uma quarta-feira. */
        val NOW: LocalDateTime = LocalDateTime.of(2026, 1, 7, 10, 0)
    }
}
