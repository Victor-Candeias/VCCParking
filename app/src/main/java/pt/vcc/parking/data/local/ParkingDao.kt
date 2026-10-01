package pt.vcc.parking.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/**
 * O SQLite nao calcula distancias geograficas, por isso a leitura usa uma caixa
 * envolvente em `latitude`/`longitude`. A ordenacao por distancia fica a cargo
 * de `nearestFrom` (`vp-04-domain`), o passo seguinte do fluxo da seccao 29.
 */
@Dao
interface ParkingDao {

    /** Substitui os registos existentes com o novo `updatedAt`. */
    @Upsert
    suspend fun upsertAll(parking: List<ParkingEntity>)

    @Query(
        "SELECT * FROM ${ParkingEntity.TABLE} " +
            "WHERE latitude BETWEEN :minLatitude AND :maxLatitude " +
            "AND longitude BETWEEN :minLongitude AND :maxLongitude",
    )
    suspend fun nearby(
        minLatitude: Double,
        maxLatitude: Double,
        minLongitude: Double,
        maxLongitude: Double,
    ): List<ParkingEntity>

    @Query(
        "SELECT MAX(updatedAt) FROM ${ParkingEntity.TABLE} " +
            "WHERE latitude BETWEEN :minLatitude AND :maxLatitude " +
            "AND longitude BETWEEN :minLongitude AND :maxLongitude",
    )
    suspend fun latestUpdateAt(
        minLatitude: Double,
        maxLatitude: Double,
        minLongitude: Double,
        maxLongitude: Double,
    ): Long?

    /** Retencao; corre apos cada gravacao. */
    @Query("DELETE FROM ${ParkingEntity.TABLE} WHERE updatedAt < :thresholdMillis")
    suspend fun deleteOlderThan(thresholdMillis: Long)
}
