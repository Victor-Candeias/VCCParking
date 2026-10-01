package pt.vcc.parking.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Tabela `parking_reminder` em memoria, para os testes de JVM.
 *
 * Recebe o [parkedCars] porque `deleteOrphans` e uma consulta sobre as duas
 * tabelas: sem o lado do estacionamento nao havia forma de reproduzir o caso
 * que a consulta existe para resolver.
 */
class FakeParkingReminderDao(
    private val parkedCars: FakeParkedCarDao? = null,
) : ParkingReminderDao {

    private val rows = MutableStateFlow<List<ParkingReminderEntity>>(emptyList())

    override suspend fun upsert(reminder: ParkingReminderEntity) {
        rows.value = rows.value.filterNot { it.parkedCarId == reminder.parkedCarId } + reminder
    }

    override fun observe(parkedCarId: Long): Flow<ParkingReminderEntity?> =
        rows.map { list -> list.firstOrNull { it.parkedCarId == parkedCarId } }

    override suspend fun byParkedCarId(parkedCarId: Long): ParkingReminderEntity? =
        rows.value.firstOrNull { it.parkedCarId == parkedCarId }

    override suspend fun all(): List<ParkingReminderEntity> = rows.value

    override suspend fun delete(parkedCarId: Long): Int =
        remove { it.parkedCarId == parkedCarId }

    override suspend fun deleteOrphans(): Int {
        val activeId = parkedCars?.active()?.id
        return remove { it.parkedCarId != activeId }
    }

    override suspend fun markDismissed(parkedCarId: Long): Int {
        val affected = rows.value.count { it.parkedCarId == parkedCarId }
        rows.value = rows.value.map {
            if (it.parkedCarId == parkedCarId) it.copy(dismissed = true) else it
        }
        return affected
    }

    private fun remove(matches: (ParkingReminderEntity) -> Boolean): Int {
        val affected = rows.value.count(matches)
        rows.value = rows.value.filterNot(matches)
        return affected
    }
}
