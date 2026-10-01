package pt.vcc.parking.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Acesso aos lembretes (`vp-11-reminders`).
 *
 * [upsert] e nao `insert` porque definir um prazo e sempre a mesma operacao do
 * ponto de vista do utilizador, quer ja existisse um lembrete quer nao.
 */
@Dao
interface ParkingReminderDao {

    @Upsert
    suspend fun upsert(reminder: ParkingReminderEntity)

    /** Observado pelo cartao do estacionamento; emite a cada alteracao do prazo. */
    @Query("SELECT * FROM ${ParkingReminderEntity.TABLE} WHERE parkedCarId = :parkedCarId")
    fun observe(parkedCarId: Long): Flow<ParkingReminderEntity?>

    @Query("SELECT * FROM ${ParkingReminderEntity.TABLE} WHERE parkedCarId = :parkedCarId")
    suspend fun byParkedCarId(parkedCarId: Long): ParkingReminderEntity?

    /** Usado ao reagendar depois de um reinicio do dispositivo. */
    @Query("SELECT * FROM ${ParkingReminderEntity.TABLE}")
    suspend fun all(): List<ParkingReminderEntity>

    @Query("DELETE FROM ${ParkingReminderEntity.TABLE} WHERE parkedCarId = :parkedCarId")
    suspend fun delete(parkedCarId: Long): Int

    /**
     * Limpa lembretes de estacionamentos que ja terminaram.
     *
     * Termos normais nunca deixam orfaos, mas um processo morto a meio do
     * `endActive` deixaria; sem esta limpeza o alarme voltaria a ser reagendado
     * no arranque seguinte para um carro que ja nao esta estacionado.
     */
    @Query(
        "DELETE FROM ${ParkingReminderEntity.TABLE} WHERE parkedCarId NOT IN (" +
            "SELECT id FROM ${ParkedCarEntity.TABLE} WHERE endedAtMillis IS NULL)",
    )
    suspend fun deleteOrphans(): Int

    @Query(
        "UPDATE ${ParkingReminderEntity.TABLE} SET dismissed = 1 WHERE parkedCarId = :parkedCarId",
    )
    suspend fun markDismissed(parkedCarId: Long): Int
}
