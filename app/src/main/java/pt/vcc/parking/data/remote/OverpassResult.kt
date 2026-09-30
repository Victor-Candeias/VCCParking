package pt.vcc.parking.data.remote

import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import kotlinx.serialization.SerializationException

sealed interface OverpassResult {

    data class Success(
        val endpoint: String,
        val elements: List<OverpassElement>,
    ) : OverpassResult

    data class Failure(
        val attempts: List<OverpassAttempt>,
    ) : OverpassResult
}

data class OverpassAttempt(
    val endpoint: String,
    val error: OverpassError,
)

/**
 * Classificacao pedida pela seccao 16 do documento do MVP. O cancelamento nao
 * aparece aqui porque e propagado em vez de classificado.
 */
sealed interface OverpassError {

    data object Timeout : OverpassError

    data object Network : OverpassError

    data class Http(val code: Int) : OverpassError

    data class Parsing(val cause: Throwable) : OverpassError

    data class Unexpected(val cause: Throwable) : OverpassError
}

/** Estados em que insistir noutro endpoint nao ajuda: a query e a mesma em todos. */
private val UNRECOVERABLE_STATUS = setOf(400, 414)

val OverpassError.isRecoverable: Boolean
    get() = when (this) {
        OverpassError.Timeout -> true
        OverpassError.Network -> true
        is OverpassError.Http -> code !in UNRECOVERABLE_STATUS
        is OverpassError.Parsing -> true
        is OverpassError.Unexpected -> false
    }

/** Descricao para diagnostico, sem incluir a query nem as coordenadas. */
val OverpassError.diagnostic: String
    get() = when (this) {
        OverpassError.Timeout -> "timeout"
        OverpassError.Network -> "rede"
        is OverpassError.Http -> "http $code"
        is OverpassError.Parsing -> "parsing ${cause.javaClass.simpleName}"
        is OverpassError.Unexpected -> "inesperado ${cause.javaClass.simpleName}"
    }

internal fun Throwable.toOverpassError(): OverpassError = when (this) {
    is SocketTimeoutException -> OverpassError.Timeout
    is InterruptedIOException -> OverpassError.Timeout
    is SerializationException -> OverpassError.Parsing(this)
    is IOException -> OverpassError.Network
    else -> OverpassError.Unexpected(this)
}
