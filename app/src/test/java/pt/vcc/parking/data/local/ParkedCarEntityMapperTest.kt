package pt.vcc.parking.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.vcc.parking.domain.model.ParkedCar

class ParkedCarEntityMapperTest {

    @Test
    fun `converting to the entity and back preserves every field`() {
        val original = ParkedCar(
            id = 7,
            latitude = 38.7336,
            longitude = -9.1447,
            accuracyMeters = 12.5f,
            parkedAtMillis = 1_700_000_000_000,
            endedAtMillis = 1_700_000_900_000,
            note = "Piso -2, lugar 134",
            photoUri = "file:///data/user/0/pt.vcc.parking/files/parked/parked-1.jpg",
            osmType = "way",
            osmId = 42,
        )

        val roundTrip = original.toEntity().toParkedCar()

        assertEquals(original, roundTrip)
    }

    @Test
    fun `an active record keeps the end date null`() {
        val active = ParkedCar(
            latitude = 38.7336,
            longitude = -9.1447,
            parkedAtMillis = 1_700_000_000_000,
        )

        val entity = active.toEntity()

        assertNull(entity.endedAtMillis)
        assertNull(entity.note)
        assertNull(entity.photoUri)
        assertNull(entity.osmType)
        assertNull(entity.osmId)
    }

    @Test
    fun `a record without an osm reference has no parking id`() {
        val entity = ParkedCarEntity(
            id = 1,
            latitude = 38.7336,
            longitude = -9.1447,
            parkedAtMillis = 1_700_000_000_000,
        )

        assertNull(entity.toParkedCar().parkingId)
    }

    @Test
    fun `the parking id uses the same identity as the parking model`() {
        val entity = ParkedCarEntity(
            id = 1,
            latitude = 38.7336,
            longitude = -9.1447,
            parkedAtMillis = 1_700_000_000_000,
            osmType = "node",
            osmId = 99,
        )

        assertEquals("node/99", entity.toParkedCar().parkingId)
    }

    @Test
    fun `a list is converted element by element`() {
        val entities = listOf(
            ParkedCarEntity(id = 1, latitude = 1.0, longitude = 2.0, parkedAtMillis = 10),
            ParkedCarEntity(id = 2, latitude = 3.0, longitude = 4.0, parkedAtMillis = 20),
        )

        assertEquals(listOf(1L, 2L), entities.toParkedCars().map { it.id })
    }
}
