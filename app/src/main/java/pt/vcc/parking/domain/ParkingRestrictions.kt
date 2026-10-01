package pt.vcc.parking.domain

import pt.vcc.parking.domain.model.Parking

/**
 * Restricoes que decidem se o carro cabe e quanto tempo pode ficar
 * (`vp-12-rich-details`).
 *
 * Agrupadas porque sao a mesma pergunta do ponto de vista de quem conduz —
 * «posso mesmo deixar aqui o carro?» — e porque assim a UI sabe de uma vez se
 * tem alguma coisa para mostrar, em vez de testar campo a campo e acabar com
 * uma seccao vazia.
 */
data class ParkingRestrictions(
    /** `null` quando o OSM nao indica limite legivel; nunca significa «sem limite». */
    val maxHeightMeters: Double? = null,
    val maxStay: String? = null,
    val condition: String? = null,
    val access: String? = null,
) {

    val isEmpty: Boolean
        get() = maxHeightMeters == null && maxStay == null && condition == null && access == null

    /**
     * `true` se um veiculo de [heightMeters] nao cabe.
     *
     * Devolve `false` sem limite conhecido: um parque que nao declara altura
     * nao pode ser dado como impossivel, ou desapareceriam quase todos.
     */
    fun excludesVehicleOf(heightMeters: Double): Boolean {
        val limit = maxHeightMeters ?: return false
        return heightMeters > limit
    }

    companion object {
        fun of(parking: Parking): ParkingRestrictions = ParkingRestrictions(
            maxHeightMeters = parking.maxHeightMeters,
            maxStay = parking.maxStay,
            condition = parking.condition,
            // O acesso so e restricao quando nao e o caso normal; «publico» nao
            // acrescenta nada a uma lista de limitacoes.
            access = parking.access?.takeUnless { it.isUnrestrictedAccess() },
        )

        private fun String.isUnrestrictedAccess(): Boolean =
            trim().lowercase() in UNRESTRICTED_ACCESS

        private val UNRESTRICTED_ACCESS = setOf("yes", "public")
    }
}
