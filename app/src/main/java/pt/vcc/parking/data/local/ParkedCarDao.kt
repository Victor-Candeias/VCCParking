package pt.vcc.parking.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Acesso aos registos de estacionamento (`vp-08-park-save`).
 *
 * E uma classe abstrata e nao uma interface porque [park] precisa de corpo:
 * terminar o anterior e inserir o novo tem de acontecer na mesma transacao,
 * caso contrario uma falha pelo meio deixaria zero ou dois registos ativos.
 */
@Dao
abstract class ParkedCarDao {

    @Insert
    abstract suspend fun insert(parkedCar: ParkedCarEntity): Long

    /** Observado pela UI; emite de novo a cada [park], [endActive] ou edicao. */
    @Query(
        "SELECT * FROM ${ParkedCarEntity.TABLE} " +
            "WHERE endedAtMillis IS NULL ORDER BY parkedAtMillis DESC LIMIT 1",
    )
    abstract fun observeActive(): Flow<ParkedCarEntity?>

    @Query(
        "SELECT * FROM ${ParkedCarEntity.TABLE} " +
            "WHERE endedAtMillis IS NULL ORDER BY parkedAtMillis DESC LIMIT 1",
    )
    abstract suspend fun active(): ParkedCarEntity?

    @Query("SELECT * FROM ${ParkedCarEntity.TABLE} WHERE id = :id")
    abstract suspend fun byId(id: Long): ParkedCarEntity?

    /** Base do historico de `vp-10-history`. */
    @Query("SELECT * FROM ${ParkedCarEntity.TABLE} ORDER BY parkedAtMillis DESC")
    abstract fun observeAll(): Flow<List<ParkedCarEntity>>

    /**
     * Pagina do historico (`vp-10-history`).
     *
     * O [limit] cresce a medida que o utilizador pede mais registos: continua a
     * ser uma unica consulta observada, pelo que apagar ou terminar um
     * estacionamento atualiza a lista sem a reconstruir do inicio.
     */
    @Query(
        "SELECT * FROM ${ParkedCarEntity.TABLE} " +
            "WHERE endedAtMillis IS NOT NULL ORDER BY parkedAtMillis DESC LIMIT :limit",
    )
    abstract fun observeHistory(limit: Int): Flow<List<ParkedCarEntity>>

    /** Permite saber se ainda ha registos para la da pagina atual. */
    @Query("SELECT COUNT(*) FROM ${ParkedCarEntity.TABLE} WHERE endedAtMillis IS NOT NULL")
    abstract fun observeHistoryCount(): Flow<Int>

    /** Leitura completa; usada pela exportacao, que nao e paginada. */
    @Query(
        "SELECT * FROM ${ParkedCarEntity.TABLE} " +
            "WHERE endedAtMillis IS NOT NULL ORDER BY parkedAtMillis DESC",
    )
    abstract suspend fun history(): List<ParkedCarEntity>

    @Query(
        "SELECT * FROM ${ParkedCarEntity.TABLE} " +
            "WHERE endedAtMillis IS NOT NULL AND endedAtMillis < :millis",
    )
    abstract suspend fun historyEndedBefore(millis: Long): List<ParkedCarEntity>

    /**
     * O `endedAtMillis IS NOT NULL` nao e redundante: protege o estacionamento
     * ativo de ser apagado por um id que chegue errado do ecra do historico.
     */
    @Query("DELETE FROM ${ParkedCarEntity.TABLE} WHERE id = :id AND endedAtMillis IS NOT NULL")
    abstract suspend fun deleteHistoryEntry(id: Long): Int

    @Query("DELETE FROM ${ParkedCarEntity.TABLE} WHERE endedAtMillis IS NOT NULL")
    abstract suspend fun deleteHistory(): Int

    /** Retencao automatica; o registo ativo nunca tem [millis] para comparar. */
    @Query(
        "DELETE FROM ${ParkedCarEntity.TABLE} " +
            "WHERE endedAtMillis IS NOT NULL AND endedAtMillis < :millis",
    )
    abstract suspend fun deleteHistoryEndedBefore(millis: Long): Int

    /** Marca o fim sem apagar: o registo passa a pertencer ao historico. */
    @Query(
        "UPDATE ${ParkedCarEntity.TABLE} SET endedAtMillis = :endedAtMillis " +
            "WHERE endedAtMillis IS NULL",
    )
    abstract suspend fun endActive(endedAtMillis: Long): Int

    @Query(
        "UPDATE ${ParkedCarEntity.TABLE} SET note = :note, photoUri = :photoUri " +
            "WHERE id = :id",
    )
    abstract suspend fun updateDetails(id: Long, note: String?, photoUri: String?): Int

    /**
     * Correcao manual da posicao.
     *
     * A precisao e limpa porque o ponto deixou de vir de uma medicao: foi o
     * utilizador que o colocou, e manter o valor antigo faria o cartao avisar
     * de uma incerteza que ja nao existe.
     */
    @Query(
        "UPDATE ${ParkedCarEntity.TABLE} " +
            "SET latitude = :latitude, longitude = :longitude, accuracyMeters = NULL " +
            "WHERE id = :id",
    )
    abstract suspend fun updatePosition(id: Long, latitude: Double, longitude: Double): Int

    /**
     * Garante o invariante de no maximo um registo com `endedAtMillis IS NULL`:
     * o anterior e terminado no mesmo instante em que o novo comeca.
     */
    @Transaction
    open suspend fun park(parkedCar: ParkedCarEntity): Long {
        endActive(parkedCar.parkedAtMillis)
        return insert(parkedCar)
    }
}
