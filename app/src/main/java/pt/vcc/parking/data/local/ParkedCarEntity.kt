package pt.vcc.parking.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tabela do estacionamento do utilizador (`vp-08-park-save`).
 *
 * A chave e um id proprio e nao a posicao: o mesmo lugar pode ser usado varias
 * vezes e cada utilizacao e um registo distinto no historico.
 *
 * O indice e sobre [endedAtMillis] porque a consulta mais frequente — o
 * estacionamento ativo — filtra exatamente por esta coluna.
 */
@Entity(
    tableName = ParkedCarEntity.TABLE,
    indices = [Index(value = ["endedAtMillis"])],
)
data class ParkedCarEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val parkedAtMillis: Long,
    val endedAtMillis: Long? = null,
    val note: String? = null,
    val photoUri: String? = null,
    val osmType: String? = null,
    val osmId: Long? = null,
) {
    companion object {
        const val TABLE = "parked_car"
    }
}
