package pt.vcc.parking.data.local

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import pt.vcc.parking.domain.model.Parking

/**
 * Implementacao do contrato sobre o [ParkingDao].
 *
 * A caixa envolvente e maior do que o circulo do raio pedido, pelo que pode
 * devolver alguns parques de fora; a diferenca e irrelevante porque a lista e
 * ordenada por distancia antes de ser mostrada.
 */
class RoomParkingCache(private val dao: ParkingDao) : ParkingCache {

    override suspend fun nearby(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
    ): List<Parking> {
        val box = BoundingBox.around(latitude, longitude, radiusMeters)
        return dao.nearby(
            minLatitude = box.minLatitude,
            maxLatitude = box.maxLatitude,
            minLongitude = box.minLongitude,
            maxLongitude = box.maxLongitude,
        ).toParking()
    }

    override suspend fun latestUpdateAt(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
    ): Long? {
        val box = BoundingBox.around(latitude, longitude, radiusMeters)
        return dao.latestUpdateAt(
            minLatitude = box.minLatitude,
            maxLatitude = box.maxLatitude,
            minLongitude = box.minLongitude,
            maxLongitude = box.maxLongitude,
        )
    }

    override suspend fun save(parking: List<Parking>, updatedAtMillis: Long) {
        if (parking.isEmpty()) return
        dao.upsertAll(parking.toEntities(updatedAtMillis))
    }

    override suspend fun deleteOlderThan(thresholdMillis: Long) {
        dao.deleteOlderThan(thresholdMillis)
    }
}

/** Caixa envolvente em graus, calculada no Kotlin porque o SQLite nao o faz. */
internal data class Bounds(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double,
)

internal object BoundingBox {

    private const val METERS_PER_DEGREE_LATITUDE = 111_320.0

    /**
     * Perto dos polos `cos(latitude)` tende para zero e a margem de longitude
     * explodiria. O limite inferior corresponde a cerca de 89,4 graus.
     */
    private const val MIN_COSINE = 0.01

    fun around(latitude: Double, longitude: Double, radiusMeters: Int): Bounds {
        val latitudeMargin = radiusMeters / METERS_PER_DEGREE_LATITUDE
        val cosine = max(MIN_COSINE, abs(cos(Math.toRadians(latitude))))
        val longitudeMargin = latitudeMargin / cosine

        val minLongitude = longitude - longitudeMargin
        val maxLongitude = longitude + longitudeMargin

        // A caixa deixa de ser util quando da a volta ao antimeridiano; abrir
        // a longitude toda devolve mais registos, que a ordenacao corrige.
        val wrapsAntimeridian = minLongitude < -180.0 || maxLongitude > 180.0

        return Bounds(
            minLatitude = (latitude - latitudeMargin).coerceAtLeast(-90.0),
            maxLatitude = (latitude + latitudeMargin).coerceAtMost(90.0),
            minLongitude = if (wrapsAntimeridian) -180.0 else minLongitude,
            maxLongitude = if (wrapsAntimeridian) 180.0 else maxLongitude,
        )
    }
}
