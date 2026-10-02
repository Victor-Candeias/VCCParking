package pt.vcc.parking.car

/**
 * Regras da lista projetada que nao dependem do SDK do carro.
 *
 * O numero de linhas visiveis nao e uma constante da app: varia com o veiculo e
 * e o host que o anuncia atraves do `ConstraintManager`. Aqui fica apenas o que
 * fazer com esse valor depois de o receber.
 */
object CarPlaceLabel {

    /** `PlaceMarker` recusa etiquetas mais longas do que isto. */
    const val MAX_MARKER_LABEL_LENGTH = 3

    /**
     * Quando o host anuncia um limite invalido vale mais mostrar alguma coisa do
     * que um ecra vazio; abaixo de um parque a lista deixaria de ter utilidade.
     */
    const val MINIMUM_PLACES = 1

    /**
     * Etiqueta do marcador no mapa: a posicao na lista, comecando em 1, para que
     * o pino e a linha correspondente se leiam como o mesmo item.
     *
     * Acima de 999 a etiqueta nao cabe, mas esse caso nao chega a existir — o
     * limite do host fica sempre em poucas dezenas.
     */
    fun markerLabel(index: Int): String =
        (index + 1).toString().takeLast(MAX_MARKER_LABEL_LENGTH)

    /** Corta a lista ao que o host aceita mostrar, sem alterar a ordem. */
    fun <T> limitedTo(items: List<T>, contentLimit: Int): List<T> =
        items.take(maxOf(contentLimit, MINIMUM_PLACES))
}
