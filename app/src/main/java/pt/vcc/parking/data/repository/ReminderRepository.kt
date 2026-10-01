package pt.vcc.parking.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import pt.vcc.parking.data.local.ParkedCarDao
import pt.vcc.parking.data.local.ParkingReminderDao
import pt.vcc.parking.data.local.toEntity
import pt.vcc.parking.data.local.toParkingReminder
import pt.vcc.parking.data.local.toParkingReminders
import pt.vcc.parking.domain.model.ParkingReminder

/**
 * Regras dos lembretes de estacionamento (`vp-11-reminders`).
 *
 * Nao agenda nada: a persistencia e o `AlarmManager` sao problemas distintos e
 * juntar os dois tornaria impossivel testar as regras sem Android. Quem guarda
 * um lembrete e responsavel por pedir o agendamento a seguir.
 *
 * O relogio entra por construtor, como em `vp-05-cache` e `vp-08-park-save`.
 */
class ReminderRepository(
    private val reminders: ParkingReminderDao,
    private val parkedCars: ParkedCarDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    /** Lembrete do estacionamento a decorrer; `null` sem carro ou sem lembrete. */
    fun observeActive(): Flow<ParkingReminder?> =
        parkedCars.observeActive().flatMapLatest { parkedCar ->
            val id = parkedCar?.id ?: return@flatMapLatest flowOf(null)
            reminders.observe(id).map { it?.toParkingReminder() }
        }

    suspend fun activeReminder(): ParkingReminder? {
        val parkedCarId = parkedCars.active()?.id ?: return null
        return reminders.byParkedCarId(parkedCarId)?.toParkingReminder()
    }

    suspend fun byParkedCarId(parkedCarId: Long): ParkingReminder? =
        reminders.byParkedCarId(parkedCarId)?.toParkingReminder()

    /**
     * Define o prazo do estacionamento ativo.
     *
     * [durationMillis] e a duracao pedida e nao um instante: o utilizador
     * escolhe «2 horas», nao «as 16h32». Converter aqui mantem a conta num
     * unico sitio e com um relogio que os testes controlam.
     */
    suspend fun setDeadline(
        parkedCarId: Long,
        durationMillis: Long?,
        warnBeforeMillis: Long = ParkingReminder.DEFAULT_WARN_BEFORE_MILLIS,
        recurringEveryMillis: Long? = null,
    ): ParkingReminder {
        val existing = reminders.byParkedCarId(parkedCarId)?.toParkingReminder()
        val reminder = ParkingReminder(
            parkedCarId = parkedCarId,
            expiresAtMillis = durationMillis?.let { now() + it },
            warnBeforeMillis = warnBeforeMillis,
            recurringEveryMillis = recurringEveryMillis ?: existing?.recurringEveryMillis,
            // Um prazo novo e um aviso novo: manter `dismissed` silenciaria o
            // lembrete que o utilizador acabou de pedir.
            dismissed = false,
        )
        reminders.upsert(reminder.toEntity())
        return reminder
    }

    /** Guarda as preferencias sem mexer no prazo ja definido. */
    suspend fun updatePreferences(
        parkedCarId: Long,
        warnBeforeMillis: Long,
        recurringEveryMillis: Long?,
    ): ParkingReminder {
        val existing = reminders.byParkedCarId(parkedCarId)?.toParkingReminder()
            ?: ParkingReminder(parkedCarId = parkedCarId)
        val updated = existing.copy(
            warnBeforeMillis = warnBeforeMillis,
            recurringEveryMillis = recurringEveryMillis,
        )
        reminders.upsert(updated.toEntity())
        return updated
    }

    /** «Mais 30 min» da notificacao; `null` quando nao havia lembrete nenhum. */
    suspend fun extend(
        parkedCarId: Long,
        extraMillis: Long = ParkingReminder.EXTENSION_MILLIS,
    ): ParkingReminder? {
        val existing = reminders.byParkedCarId(parkedCarId)?.toParkingReminder() ?: return null
        val extended = existing.extendedBy(extraMillis, now())
        reminders.upsert(extended.toEntity())
        return extended
    }

    /** O utilizador fechou o aviso: nao volta a ser agendado para este prazo. */
    suspend fun dismiss(parkedCarId: Long): Boolean = reminders.markDismissed(parkedCarId) > 0

    suspend fun clear(parkedCarId: Long): Boolean = reminders.delete(parkedCarId) > 0

    /**
     * Lembretes a reagendar depois de um reinicio.
     *
     * Limpa primeiro os orfaos, para nao ressuscitar alarmes de carros que ja
     * nao estao estacionados.
     */
    suspend fun pending(): List<ParkingReminder> {
        reminders.deleteOrphans()
        return reminders.all().toParkingReminders().filterNot { it.isSilent }
    }
}
