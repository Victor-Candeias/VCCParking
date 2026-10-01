package pt.vcc.parking.domain

import pt.vcc.parking.domain.model.Parking

/**
 * Normalizacao do preco a partir de `fee`, `charge` e `fee:conditional`
 * (`vp-12-rich-details`).
 *
 * O OSM nao e um tarifario: os valores podem estar desatualizados e a sintaxe
 * de `charge` e livre. Esta classe nao tenta converter moedas nem calcular o
 * custo de uma estadia — limita-se a dizer de que caso se trata e a entregar o
 * texto original quando nao consegue mais do que isso.
 */
sealed interface ParkingCharge {

    /** `fee=no`: gratuito sem condicoes. */
    data object Free : ParkingCharge

    /**
     * `fee=yes` com `charge` legivel.
     *
     * [amount] e [currency] ficam separados para a UI poder formatar o valor na
     * locale do dispositivo; [unit] e o texto do periodo tal como vem do OSM
     * (`h`, `day`, `30 min`), que nao tem vocabulario fechado.
     */
    data class Priced(
        val amount: Double,
        val currency: String,
        val unit: String? = null,
    ) : ParkingCharge

    /** `fee=yes` sem `charge`, ou com um `charge` que nao se consegue ler. */
    data class PricedUnknownAmount(val raw: String? = null) : ParkingCharge

    /** Condicional (`fee:conditional`): o valor em bruto e o mais honesto que ha. */
    data class Conditional(val raw: String) : ParkingCharge

    /** Sem informacao nenhuma sobre preco. */
    data object Unknown : ParkingCharge

    companion object {

        /**
         * Lê o preco de [parking].
         *
         * `fee:conditional` tem precedencia sobre `fee` porque e mais
         * especifico: dizer «pago» a quem chega as 21:00 a um parque que e
         * gratuito a partir das 20:00 esta errado, ainda que `fee=yes`.
         */
        fun of(parking: Parking): ParkingCharge {
            parking.feeConditional
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return Conditional(it) }

            return when (parking.isFree) {
                true -> Free
                false -> parsePrice(parking.charge) ?: PricedUnknownAmount(parking.charge)
                null -> parking.fee?.let { PricedUnknownAmount(it) } ?: Unknown
            }
        }

        /**
         * Lê `charge` no formato recomendado pelo OSM: `1.20 EUR/h`.
         *
         * Formatos fora deste nao sao adivinhados — devolver `null` leva a UI a
         * mostrar o texto original, que o utilizador percebe melhor do que uma
         * interpretacao errada.
         */
        fun parsePrice(charge: String?): Priced? {
            val raw = charge?.trim()?.takeIf { it.isNotEmpty() } ?: return null

            val (value, rest) = raw.split(' ', limit = 2).let {
                if (it.size == 2) it[0] to it[1] else return null
            }

            val amount = value.replace(',', '.').toDoubleOrNull() ?: return null
            if (!amount.isFinite() || amount < 0.0) return null

            val currency = rest.substringBefore(UNIT_SEPARATOR).trim().uppercase()
            if (currency.length != CURRENCY_CODE_LENGTH || !currency.all(Char::isLetter)) {
                return null
            }

            val unit = rest.substringAfter(UNIT_SEPARATOR, "").trim().takeIf { it.isNotEmpty() }

            return Priced(amount, currency, unit)
        }

        private const val UNIT_SEPARATOR = "/"
        private const val CURRENCY_CODE_LENGTH = 3
    }
}
