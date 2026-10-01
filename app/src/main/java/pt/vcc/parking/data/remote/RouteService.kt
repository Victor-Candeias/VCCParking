package pt.vcc.parking.data.remote

import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import kotlinx.serialization.SerializationException
import pt.vcc.parking.domain.model.WalkingRoute

/**
 * Contrato do routing pedonal (`vp-09-return-route`).
 *
 * A UI depende desta interface e nunca do fornecedor: as instancias publicas de
 * routing mudam de endereco e de politica com a mesma frequencia das do
 * Overpass, e trocar de servico nao pode obrigar a mexer no ecra.
 */
interface RouteService {

    suspend fun walkingRoute(
        startLatitude: Double,
        startLongitude: Double,
        endLatitude: Double,
        endLongitude: Double,
    ): RouteResult
}

sealed interface RouteResult {

    data class Success(val endpoint: String, val route: WalkingRoute) : RouteResult

    /**
     * O servico respondeu mas nao ha caminho a pe — acontece quando o carro
     * ficou num piso sem ligacao pedonal mapeada. Nao e falha de rede, por isso
     * insistir noutro endpoint nao traria resposta diferente.
     */
    data object Empty : RouteResult

    data class Failure(val attempts: List<RouteAttempt>) : RouteResult
}

data class RouteAttempt(
    val endpoint: String,
    val error: RouteError,
)

/** Mesma classificacao de [OverpassError], aplicada ao routing. */
sealed interface RouteError {

    data object Timeout : RouteError

    data object Network : RouteError

    data class Http(val code: Int) : RouteError

    data class Parsing(val cause: Throwable) : RouteError

    data class Unexpected(val cause: Throwable) : RouteError
}

/** Um pedido mal formado e igual em todas as instancias; repeti-lo nao ajuda. */
private val UNRECOVERABLE_STATUS = setOf(400, 414)

val RouteError.isRecoverable: Boolean
    get() = when (this) {
        RouteError.Timeout -> true
        RouteError.Network -> true
        is RouteError.Http -> code !in UNRECOVERABLE_STATUS
        is RouteError.Parsing -> true
        is RouteError.Unexpected -> false
    }

/** Diagnostico sem coordenadas: a rota revela origem e destino do utilizador. */
val RouteError.diagnostic: String
    get() = when (this) {
        RouteError.Timeout -> "timeout"
        RouteError.Network -> "rede"
        is RouteError.Http -> "http $code"
        is RouteError.Parsing -> "parsing ${cause.javaClass.simpleName}"
        is RouteError.Unexpected -> "inesperado ${cause.javaClass.simpleName}"
    }

internal fun Throwable.toRouteError(): RouteError = when (this) {
    is SocketTimeoutException -> RouteError.Timeout
    is InterruptedIOException -> RouteError.Timeout
    is SerializationException -> RouteError.Parsing(this)
    is IOException -> RouteError.Network
    else -> RouteError.Unexpected(this)
}
