package pt.vcc.parking.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.vcc.parking.domain.model.Parking

class ParkingEntityMapperTest {

    @Test
    fun `preserves every field through the round trip`() {
        val entity = COMPLETE.toEntity(UPDATED_AT)

        assertEquals(COMPLETE, entity.toParking())
    }

    @Test
    fun `keeps the save instant in the entity`() {
        assertEquals(UPDATED_AT, COMPLETE.toEntity(UPDATED_AT).updatedAt)
    }

    @Test
    fun `does not persist the distance because it depends on the user position`() {
        val measured = COMPLETE.copy(distanceMeters = 123.4)

        assertNull(measured.toEntity(UPDATED_AT).toParking().distanceMeters)
    }

    @Test
    fun `keeps absent tags as null instead of inventing values`() {
        val minimal = Parking(osmId = 2L, osmType = "way", latitude = 38.7, longitude = -9.1)

        val restored = minimal.toEntity(UPDATED_AT).toParking()

        assertNull(restored.name)
        assertNull(restored.capacity)
        assertNull(restored.access)
        assertEquals(minimal, restored)
    }

    @Test
    fun `converts lists in both directions`() {
        val parking = listOf(COMPLETE, COMPLETE.copy(osmId = 9L, osmType = "relation"))

        val entities = parking.toEntities(UPDATED_AT)

        assertEquals(listOf("node/1", "relation/9"), entities.toParking().map { it.id })
    }

    private companion object {
        const val UPDATED_AT = 1_700_000_000_000L

        val COMPLETE = Parking(
            osmId = 1L,
            osmType = "node",
            latitude = 38.736946,
            longitude = -9.142685,
            name = "Parque do Marques",
            parkingType = "underground",
            capacity = 120,
            operator = "EMEL",
            access = "yes",
            fee = "yes",
            openingHours = "24/7",
            disabledCapacity = 4,
            zone = "verde",
            zoneColour = "green",
            phone = "+351210000000",
            website = "https://example.test",
        )
    }
}
