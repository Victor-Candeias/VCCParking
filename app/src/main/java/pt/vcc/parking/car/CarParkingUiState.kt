package pt.vcc.parking.car

import pt.vcc.parking.domain.model.Parking

/**
 * Estados da interface projetada (`vp-16-android-auto`).
 *
 * Deliberadamente separado de `ParkingUiState`: o telemovel distingue cache de
 * rede para mostrar o aviso da seccao 26, e o carro nao tem onde o mostrar sem
 * roubar espaco a lista. Em troca, o carro precisa de um estado proprio para a
 * permissao em falta, que no telemovel e tratado pelo `LocationViewModel`.
 *
 * Nenhum destes estados conhece o SDK do carro, para que a decisao continue a
 * ser testavel em JVM.
 */
sealed interface CarParkingUiState {

    data object Loading : CarParkingUiState

    /** [parking] ja vem sem privados, com distancia e ordenado por `nearestFrom`. */
    data class Ready(
        val parking: List<Parking>,
        val latitude: Double,
        val longitude: Double,
    ) : CarParkingUiState

    /** Pesquisa bem sucedida sem nenhum parque no raio. */
    data object Empty : CarParkingUiState

    /**
     * A permissao de localizacao e concedida no telemovel e nunca aqui: pedi-la
     * com o veiculo em andamento e precisamente o tipo de interacao que as
     * regras de distracao proibem.
     */
    data object PermissionMissing : CarParkingUiState

    /** Localizacao desligada no sistema ou sem leitura utilizavel. */
    data object LocationUnavailable : CarParkingUiState

    /** Overpass indisponivel e sem cache que sirva de resposta. */
    data object Failure : CarParkingUiState
}
