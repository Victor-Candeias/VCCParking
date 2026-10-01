package pt.vcc.parking.domain.model

/**
 * Modelo interno da seccao 8 do documento do MVP.
 *
 * As tags OSM nao circulam para a UI. Um campo a `null` significa informacao
 * indisponivel (seccao 9) e nunca `false`: a UI mostra apenas o que existe.
 *
 * `vp-12-rich-details` acrescentou campos, e a regra manteve-se: os booleanos
 * sao `Boolean?` de proposito, porque «o OSM nao diz» e uma terceira resposta
 * que nao pode ser confundida com «nao».
 */
data class Parking(
    val osmId: Long,
    val osmType: String,
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
    /** `vp-12-rich-details`: tags que respondem a «posso mesmo estacionar aqui?». */
    val charge: String? = null,
    val feeConditional: String? = null,
    val maxHeightMeters: Double? = null,
    val maxStay: String? = null,
    val condition: String? = null,
    val supervised: Boolean? = null,
    val covered: Boolean? = null,
    val chargingCapacity: Int? = null,
    val parentCapacity: Int? = null,
    val paymentMethods: List<String> = emptyList(),
    val distanceMeters: Double? = null,
) {

    /** O id OSM so e unico dentro do tipo; `vp-05-cache` precisa de chave estavel. */
    val id: String get() = "$osmType/$osmId"

    /** Seccao 10: so o valor explicito `private` exclui o parque. */
    val isPrivate: Boolean get() = access?.trim().equals(ACCESS_PRIVATE, ignoreCase = true)

    /**
     * `true` so com `fee=no` explicito.
     *
     * A ausencia da tag devolve `null` e nao `false`: a maioria dos parques do
     * OSM nao a tem, e tratar isso como «pago» ou «gratuito» seria inventar.
     */
    val isFree: Boolean?
        get() = when (fee?.trim()?.lowercase()) {
            null -> null
            FEE_NO -> true
            FEE_YES -> false
            // `interval`, `donation` e afins sao condicoes, nao um sim ou nao.
            else -> null
        }

    /** Pelo menos um lugar reservado a mobilidade reduzida, quando ha informacao. */
    val hasDisabledSpaces: Boolean? get() = disabledCapacity?.let { it > 0 }

    /** Pelo menos um lugar com carregamento eletrico, quando ha informacao. */
    val hasChargingSpaces: Boolean? get() = chargingCapacity?.let { it > 0 }

    companion object {
        const val ACCESS_PRIVATE = "private"
        const val FEE_YES = "yes"
        const val FEE_NO = "no"
    }
}
