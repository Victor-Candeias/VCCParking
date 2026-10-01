package pt.vcc.parking.data.local

import pt.vcc.parking.domain.model.ParkingReminder

/** Conversao entre a tabela `parking_reminder` e o modelo de dominio. */
fun ParkingReminder.toEntity(): ParkingReminderEntity = ParkingReminderEntity(
    parkedCarId = parkedCarId,
    expiresAtMillis = expiresAtMillis,
    warnBeforeMillis = warnBeforeMillis,
    recurringEveryMillis = recurringEveryMillis,
    dismissed = dismissed,
)

fun ParkingReminderEntity.toParkingReminder(): ParkingReminder = ParkingReminder(
    parkedCarId = parkedCarId,
    expiresAtMillis = expiresAtMillis,
    warnBeforeMillis = warnBeforeMillis,
    recurringEveryMillis = recurringEveryMillis,
    dismissed = dismissed,
)

fun List<ParkingReminderEntity>.toParkingReminders(): List<ParkingReminder> =
    map { it.toParkingReminder() }
