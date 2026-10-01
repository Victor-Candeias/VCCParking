package pt.vcc.parking.returnroute

import pt.vcc.parking.domain.GeoBearing
import pt.vcc.parking.domain.model.ParkedCar
import pt.vcc.parking.domain.model.WalkingRoute
import pt.vcc.parking.location.UserLocation

/** Valor de «Esta a chegar» fixado na fase 2 da spec. */
const val ARRIVAL_RADIUS_METERS = 50.0

/**
 * Estados do regresso ao carro (`vp-09-return-route`).
 *
 * Os quatro estados da spec — sem carro, bussola, rota e chegou — nao sao quatro
 * tipos: bussola, rota e chegada partilham tudo o que o ecra mostra e so diferem
 * em dois campos. Separa-los obrigaria a repetir a mesma informacao tres vezes e
 * a UI a desenhar o mesmo em tres ramos.
 */
sealed interface ReturnUiState {

    /** A espera da primeira leitura de posicao. */
    data object Locating : ReturnUiState

    /** Nada guardado: o ecra explica e oferece voltar atras para guardar. */
    data object NoParkedCar : ReturnUiState

    data class Guiding(
        val parkedCar: ParkedCar,
        val userLocation: UserLocation,
        val distanceMeters: Double,
        /** Rumo para o carro, a partir do norte. */
        val bearingDegrees: Double,
        /** `null` sem sensor de rotacao: fica so a distancia. */
        val headingDegrees: Float? = null,
        /** `null` enquanto o routing nao responde ou nao esta disponivel. */
        val route: WalkingRoute? = null,
        /** O routing foi tentado e nao deu caminho; o aviso e discreto. */
        val routeUnavailable: Boolean = false,
    ) : ReturnUiState {

        /**
         * Tao perto que uma rota deixa de ajudar: a esta distancia o carro esta
         * a vista, e uma linha no mapa so distrai de olhar em volta.
         */
        val arrived: Boolean get() = distanceMeters <= ARRIVAL_RADIUS_METERS

        /** Angulo para rodar a seta; `null` quando nao ha bussola. */
        val relativeBearingDegrees: Double?
            get() = headingDegrees?.let {
                GeoBearing.relativeDegrees(bearingDegrees, it.toDouble())
            }
    }
}
