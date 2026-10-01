package pt.vcc.parking.ui

import pt.vcc.parking.data.repository.ParkingRepository
import pt.vcc.parking.domain.model.Parking

/**
 * Estados da UI definidos na seccao 26 do documento do MVP.
 *
 * [Idle] nao consta da seccao 26 mas e necessario: entre o arranque e a primeira
 * localizacao nao ha nada a carregar nem a mostrar.
 */
sealed interface ParkingUiState {

    data object Idle : ParkingUiState

    data object Loading : ParkingUiState

    data class Success(
        /** Ja filtrada, com distancia ao utilizador e ordenada (seccao 29). */
        val parking: List<Parking>,
        /** A seccao 26 exige que a UI saiba quando esta a mostrar cache. */
        val fromCache: Boolean,
        /** Epoch millis da gravacao dos dados apresentados. */
        val updatedAtMillis: Long,
    ) : ParkingUiState {

        /**
         * Distingue os dois motivos para [fromCache], que o repositorio nao separa.
         *
         * A seccao 18 garante que a cache so e servida sem contactar o Overpass
         * enquanto for mais recente do que a janela de frescura. Logo, uma cache
         * mais antiga do que essa janela so pode estar a ser mostrada porque a
         * atualizacao falhou, que e o caso da segunda mensagem da seccao 27.
         */
        fun isStale(nowMillis: Long): Boolean =
            fromCache && nowMillis - updatedAtMillis >= ParkingRepository.FRESHNESS_WINDOW_MILLIS
    }

    /** Overpass indisponivel e sem cache utilizavel (primeira mensagem da seccao 27). */
    data object Error : ParkingUiState
}
