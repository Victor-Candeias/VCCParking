package pt.vcc.parking.parked

import pt.vcc.parking.domain.model.ParkedCar

/**
 * Estados da funcionalidade «onde deixei o carro» (`vp-08-park-save`).
 */
sealed interface ParkedCarUiState {

    /** Nenhum carro estacionado; o ecra mostra apenas a accao de guardar. */
    data object Empty : ParkedCarUiState

    /** A obter a posicao; dura no maximo `CAPTURE_TIMEOUT_MILLIS`. */
    data object Capturing : ParkedCarUiState

    data class Active(val parkedCar: ParkedCar) : ParkedCarUiState

    /**
     * A captura falhou. Guarda o [parkedCar] anterior para que uma tentativa
     * falhada nao faca desaparecer do ecra um estacionamento que continua ativo.
     */
    data class CaptureFailed(
        val reason: ParkedCarCaptureError,
        val parkedCar: ParkedCar? = null,
    ) : ParkedCarUiState
}

/**
 * Motivos para nao ter sido possivel capturar a posicao.
 *
 * Todos terminam na mesma saida — marcar o ponto a mao no mapa — mas so
 * [PermissionMissing] justifica voltar a pedir a permissao ao utilizador.
 */
enum class ParkedCarCaptureError {
    PermissionMissing,
    LocationDisabled,
    Unavailable,
}
