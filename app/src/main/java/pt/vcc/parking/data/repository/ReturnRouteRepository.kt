package pt.vcc.parking.data.repository

import android.util.Log
import pt.vcc.parking.data.remote.RouteResult
import pt.vcc.parking.data.remote.RouteService
import pt.vcc.parking.data.remote.diagnostic
import pt.vcc.parking.domain.model.WalkingRoute

/**
 * Rota ate ao carro com degradacao para o modo bussola (`vp-09-return-route`).
 *
 * Devolve `null` em vez de um erro porque, deste lado, falhar nao e um estado
 * proprio: o ecra continua a mostrar direcao e distancia, que nao dependem de
 * rede nenhuma. Distinguir «sem caminho» de «sem resposta» so acrescentaria uma
 * mensagem que nao muda nada do que o utilizador pode fazer a seguir.
 */
class ReturnRouteRepository(private val service: RouteService) {

    suspend fun walkingRoute(
        startLatitude: Double,
        startLongitude: Double,
        endLatitude: Double,
        endLongitude: Double,
    ): WalkingRoute? {
        val result = service.walkingRoute(
            startLatitude = startLatitude,
            startLongitude = startLongitude,
            endLatitude = endLatitude,
            endLongitude = endLongitude,
        )

        return when (result) {
            is RouteResult.Success -> result.route

            RouteResult.Empty -> {
                Log.i(TAG, "Sem caminho pedonal ate ao carro")
                null
            }

            is RouteResult.Failure -> {
                Log.w(
                    TAG,
                    "Routing indisponivel apos ${result.attempts.size} tentativa(s): " +
                        result.attempts.joinToString { it.error.diagnostic },
                )
                null
            }
        }
    }

    private companion object {
        const val TAG = "ReturnRouteRepository"
    }
}
