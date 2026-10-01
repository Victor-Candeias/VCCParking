package pt.vcc.parking.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import pt.vcc.parking.data.local.HistoryRetention
import pt.vcc.parking.data.local.HistorySettings
import pt.vcc.parking.data.local.ParkedCarDao
import pt.vcc.parking.data.local.ParkedPhotoStore
import pt.vcc.parking.data.local.toEntity
import pt.vcc.parking.data.local.toParkedCar
import pt.vcc.parking.data.local.toParkedCars
import pt.vcc.parking.domain.model.ParkedCar

/**
 * Historico de estacionamentos (`vp-10-history`).
 *
 * Trabalha apenas sobre registos terminados: o estacionamento ativo pertence a
 * `ParkedCarRepository` e nao pode ser apagado por engano a partir de um ecra
 * que o utilizador abriu para rever o passado.
 *
 * O relogio entra por construtor, como em `vp-05-cache` e `vp-08-park-save`,
 * para que a retencao possa ser testada sem depender da hora da maquina.
 */
class ParkingHistoryRepository(
    private val dao: ParkedCarDao,
    private val settings: HistorySettings,
    private val photos: ParkedPhotoStore = ParkedPhotoStore.None,
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    val retention: Flow<HistoryRetention> = settings.retention

    /** Pagina observada; [limit] cresce quando o utilizador pede mais. */
    fun observeHistory(limit: Int): Flow<List<ParkedCar>> =
        dao.observeHistory(limit).map { it.toParkedCars() }

    /** Total de registos terminados, para saber se ha mais alem da pagina. */
    fun observeCount(): Flow<Int> = dao.observeHistoryCount()

    suspend fun history(): List<ParkedCar> = dao.history().toParkedCars()

    /** Mudar a retencao aplica-a de imediato; ninguem espera pelo arranque seguinte. */
    suspend fun setRetention(retention: HistoryRetention) {
        settings.setRetention(retention)
        applyRetention()
    }

    /**
     * Apaga um registo do historico e devolve-o.
     *
     * A fotografia fica no disco: enquanto o utilizador puder anular, o
     * ficheiro ainda faz falta. Quem decide que a remocao e definitiva e
     * [discardPhoto].
     */
    suspend fun delete(id: Long): ParkedCar? {
        val entity = dao.byId(id) ?: return null
        if (entity.endedAtMillis == null) return null
        return if (dao.deleteHistoryEntry(id) > 0) entity.toParkedCar() else null
    }

    /** Anula um [delete]; o id e reposto para o registo nao mudar de identidade. */
    suspend fun restore(entry: ParkedCar): ParkedCar {
        val id = dao.insert(entry.toEntity())
        return entry.copy(id = id)
    }

    /** Torna definitiva a remocao de [entry], apagando a fotografia associada. */
    suspend fun discardPhoto(entry: ParkedCar) {
        entry.photoUri?.let { photos.remove(it) }
    }

    /** Apaga tudo o que ja terminou; o estacionamento ativo nao e tocado. */
    suspend fun deleteAll(): Int {
        val removed = dao.history()
        dao.deleteHistory()
        removed.forEach { entity -> entity.photoUri?.let { photos.remove(it) } }
        return removed.size
    }

    /**
     * Limpeza pela politica de retencao, feita no arranque do ecra em vez de um
     * trabalho periodico em segundo plano: poupar bateria para apagar linhas
     * seria o tipo de troca que nao compensa.
     */
    suspend fun applyRetention(): Int {
        val days = settings.retention.first().days ?: return 0
        val cutoff = now() - days * MILLIS_PER_DAY
        val expired = dao.historyEndedBefore(cutoff)
        if (expired.isEmpty()) return 0

        dao.deleteHistoryEndedBefore(cutoff)
        expired.forEach { entity -> entity.photoUri?.let { photos.remove(it) } }
        return expired.size
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
