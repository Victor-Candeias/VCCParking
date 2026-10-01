package pt.vcc.parking.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Tabela `parked_car` em memoria, partilhada pelos testes de JVM.
 *
 * Herda [ParkedCarDao.park] de proposito: a transacao que termina o registo
 * anterior antes de inserir o novo e codigo de producao e e o que os testes do
 * repositorio cobrem.
 *
 * [insertFailure] simula uma escrita que falha, usada para verificar que os
 * ViewModel nao deixam a app cair por causa disso.
 */
class FakeParkedCarDao(private val insertFailure: Throwable? = null) : ParkedCarDao() {

    private val rows = MutableStateFlow<List<ParkedCarEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(parkedCar: ParkedCarEntity): Long {
        insertFailure?.let { throw it }
        val id = if (parkedCar.id == 0L) nextId++ else parkedCar.id
        rows.value = rows.value + parkedCar.copy(id = id)
        return id
    }

    override fun observeActive(): Flow<ParkedCarEntity?> = rows.map { it.activeRecord() }

    override suspend fun active(): ParkedCarEntity? = rows.value.activeRecord()

    override suspend fun byId(id: Long): ParkedCarEntity? = rows.value.firstOrNull { it.id == id }

    override fun observeAll(): Flow<List<ParkedCarEntity>> = rows.map { it.newestFirst() }

    override fun observeHistory(limit: Int): Flow<List<ParkedCarEntity>> =
        rows.map { list -> list.ended().newestFirst().take(limit) }

    override fun observeHistoryCount(): Flow<Int> = rows.map { it.ended().size }

    override suspend fun history(): List<ParkedCarEntity> = rows.value.ended().newestFirst()

    override suspend fun historyEndedBefore(millis: Long): List<ParkedCarEntity> =
        rows.value.endedBefore(millis)

    override suspend fun deleteHistoryEntry(id: Long): Int =
        remove { it.id == id && it.endedAtMillis != null }

    override suspend fun deleteHistory(): Int = remove { it.endedAtMillis != null }

    override suspend fun deleteHistoryEndedBefore(millis: Long): Int =
        remove { it in rows.value.endedBefore(millis) }

    override suspend fun endActive(endedAtMillis: Long): Int {
        val affected = rows.value.count { it.endedAtMillis == null }
        rows.value = rows.value.map { row ->
            if (row.endedAtMillis == null) row.copy(endedAtMillis = endedAtMillis) else row
        }
        return affected
    }

    override suspend fun updateDetails(id: Long, note: String?, photoUri: String?): Int =
        update(id) { it.copy(note = note, photoUri = photoUri) }

    override suspend fun updatePosition(id: Long, latitude: Double, longitude: Double): Int =
        update(id) { it.copy(latitude = latitude, longitude = longitude, accuracyMeters = null) }

    private fun update(id: Long, change: (ParkedCarEntity) -> ParkedCarEntity): Int {
        val affected = rows.value.count { it.id == id }
        rows.value = rows.value.map { if (it.id == id) change(it) else it }
        return affected
    }

    private fun remove(matches: (ParkedCarEntity) -> Boolean): Int {
        val affected = rows.value.count(matches)
        rows.value = rows.value.filterNot(matches)
        return affected
    }

    private fun List<ParkedCarEntity>.activeRecord(): ParkedCarEntity? =
        filter { it.endedAtMillis == null }.maxByOrNull { it.parkedAtMillis }

    private fun List<ParkedCarEntity>.ended(): List<ParkedCarEntity> =
        filter { it.endedAtMillis != null }

    private fun List<ParkedCarEntity>.endedBefore(millis: Long): List<ParkedCarEntity> =
        filter { it.endedAtMillis != null && it.endedAtMillis < millis }

    private fun List<ParkedCarEntity>.newestFirst(): List<ParkedCarEntity> =
        sortedByDescending { it.parkedAtMillis }
}
