package pt.vcc.parking.domain.model

/**
 * Modelo interno da seccao 8 do documento do MVP.
 *
 * As tags OSM nao circulam para a UI. Um campo a `null` significa informacao
 * indisponivel (seccao 9) e nunca `false`: a UI mostra apenas o que existe.
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
    val distanceMeters: Double? = null,
) {

    /** O id OSM so e unico dentro do tipo; `vp-05-cache` precisa de chave estavel. */
    val id: String get() = "$osmType/$osmId"

    /** Seccao 10: so o valor explicito `private` exclui o parque. */
    val isPrivate: Boolean get() = access?.trim().equals(ACCESS_PRIVATE, ignoreCase = true)

    companion object {
        const val ACCESS_PRIVATE = "private"
    }
}
