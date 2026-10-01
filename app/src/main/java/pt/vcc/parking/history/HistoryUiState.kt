package pt.vcc.parking.history

import pt.vcc.parking.data.local.HistoryRetention
import pt.vcc.parking.domain.model.ParkedCar

/**
 * Estado do ecra do historico (`vp-10-history`).
 *
 * A lista e a contagem total andam juntas porque sem o total nao se sabe se ha
 * mais registos para la da pagina carregada — e sem isso o botao «mostrar mais»
 * tanto podia aparecer de menos como a mais.
 */
data class HistoryUiState(
    val entries: List<ParkedCar> = emptyList(),
    val totalCount: Int = 0,
    val retention: HistoryRetention = HistoryRetention.Default,
    val loading: Boolean = true,
    val exporting: Boolean = false,
    val message: HistoryMessage? = null,
) {

    /** Vazio e diferente de «ainda a carregar»: so um deles justifica explicar o ecra. */
    val isEmpty: Boolean get() = !loading && entries.isEmpty()

    val canLoadMore: Boolean get() = entries.size < totalCount
}

/**
 * Avisos passageiros, mostrados numa `Snackbar`.
 *
 * [Deleted] guarda o registo apagado porque anular so e possivel com ele em
 * mao: a linha ja saiu da base de dados quando a mensagem aparece.
 */
sealed interface HistoryMessage {

    data class Deleted(val entry: ParkedCar) : HistoryMessage

    data class Cleared(val count: Int) : HistoryMessage

    data object Exported : HistoryMessage

    data object ExportFailed : HistoryMessage
}
