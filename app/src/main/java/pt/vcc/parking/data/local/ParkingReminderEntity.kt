package pt.vcc.parking.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tabela dos lembretes de estacionamento (`vp-11-reminders`).
 *
 * A chave primaria e o proprio `parkedCarId`: um estacionamento tem no maximo
 * um lembrete, e usar um id proprio abriria a porta a dois prazos a competir
 * pelo mesmo carro.
 *
 * Nao ha chave estrangeira para `parked_car` de proposito. O lembrete e
 * apagado quando o estacionamento termina, pelo que nunca chega ao historico;
 * uma restricao ao nivel do SQLite obrigaria a replicar o DDL exato numa
 * migracao sem ganho nenhum em troca.
 */
@Entity(tableName = ParkingReminderEntity.TABLE)
data class ParkingReminderEntity(
    @PrimaryKey
    val parkedCarId: Long,
    val expiresAtMillis: Long? = null,
    val warnBeforeMillis: Long,
    val recurringEveryMillis: Long? = null,
    val dismissed: Boolean = false,
) {
    companion object {
        const val TABLE = "parking_reminder"
    }
}
