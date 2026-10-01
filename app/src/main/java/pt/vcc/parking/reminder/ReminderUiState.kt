package pt.vcc.parking.reminder

import pt.vcc.parking.domain.model.ParkingReminder

/**
 * Estado dos lembretes apresentado pela UI (`vp-11-reminders`).
 *
 * As duas capacidades do sistema fazem parte do estado porque mudam o que o
 * ecra pode prometer: sem notificacoes o lembrete nao avisa de todo, e sem
 * alarme exato avisa, mas pode chegar tarde. Esconder isso seria mentir ao
 * utilizador precisamente sobre aquilo que ele quer garantir.
 */
data class ReminderUiState(
    val parkedCarId: Long? = null,
    val reminder: ParkingReminder? = null,
    val notificationsEnabled: Boolean = true,
    val canScheduleExact: Boolean = true,
) {

    /** Sem carro estacionado nao ha lembrete a definir. */
    val isAvailable: Boolean get() = parkedCarId != null

    val hasDeadline: Boolean get() = reminder?.hasDeadline == true
}
