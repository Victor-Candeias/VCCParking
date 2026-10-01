package pt.vcc.parking.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * Tabela da seccao 17 do documento do MVP.
 *
 * A chave e composta por [osmType] e [osmId] porque o id OSM so e unico dentro
 * do tipo — e a mesma identidade que `Parking.id` ja expoe.
 *
 * `distanceMeters` nao e persistido: depende da posicao do utilizador, que muda
 * a cada pesquisa, e e recalculado na leitura por `nearestFrom`.
 */
@Entity(
    tableName = ParkingEntity.TABLE,
    primaryKeys = ["osmType", "osmId"],
    indices = [Index(value = ["latitude", "longitude"])],
)
data class ParkingEntity(
    val osmType: String,
    val osmId: Long,
    val latitude: Double,
    val longitude: Double,
    val name: String? = null,
    val parkingType: String? = null,
    val capacity: Int? = null,
    val operator: String? = null,
    val access: String? = null,
    val fee: String? = null,
    val openingHours: String? = null,
    val disabledCapacity: Int? = null,
    val zone: String? = null,
    val zoneColour: String? = null,
    val phone: String? = null,
    val website: String? = null,
    /** `vp-12-rich-details`; `paymentMethods` e guardado como lista separada por `;`. */
    val charge: String? = null,
    val feeConditional: String? = null,
    val maxHeightMeters: Double? = null,
    val maxStay: String? = null,
    val condition: String? = null,
    val supervised: Boolean? = null,
    val covered: Boolean? = null,
    val chargingCapacity: Int? = null,
    val parentCapacity: Int? = null,
    val paymentMethods: String? = null,
    /** Epoch millis da gravacao; base da politica de frescura da seccao 18. */
    val updatedAt: Long,
) {
    companion object {
        const val TABLE = "parking"

        /**
         * Separador dos meios de pagamento.
         *
         * E o mesmo que o OSM usa em valores multiplos e nao aparece dentro de
         * um nome de meio de pagamento, pelo que nao ha nada a escapar.
         */
        const val PAYMENT_SEPARATOR = ";"
    }
}
