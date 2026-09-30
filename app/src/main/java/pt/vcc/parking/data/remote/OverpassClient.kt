package pt.vcc.parking.data.remote

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import pt.vcc.parking.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Percorre os endpoints Overpass por ordem e devolve a primeira resposta
 * valida, conforme a estrategia de fallback da seccao 6 do documento do MVP.
 *
 * Nao lanca excecoes de rede para fora: devolve [OverpassResult.Failure] para
 * que o repositorio possa decidir entre cache e erro sem inspecionar excecoes.
 */
class OverpassClient(
    private val api: OverpassApi,
    private val endpoints: List<String> = OverpassEndpoints.DEFAULT,
    private val elapsedMillis: () -> Long = SystemClock::elapsedRealtime,
) : OverpassService {

    init {
        require(endpoints.isNotEmpty()) { "E necessario pelo menos um endpoint Overpass" }
    }

    override suspend fun searchParking(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
    ): OverpassResult {
        val query = OverpassQuery.buildParkingQuery(latitude, longitude, radiusMeters)
        val attempts = mutableListOf<OverpassAttempt>()

        for ((index, endpoint) in endpoints.withIndex()) {
            val startedAt = elapsedMillis()
            val attempt = attempt(endpoint, query)
            val durationMillis = elapsedMillis() - startedAt

            when (attempt) {
                is Attempt.Ok -> {
                    Log.i(
                        TAG,
                        "Endpoint $index respondeu ${attempt.status} em ${durationMillis}ms: " +
                            "${attempt.received} elementos, ${attempt.elements.size} com coordenadas",
                    )
                    return OverpassResult.Success(endpoint, attempt.elements)
                }

                is Attempt.Failed -> {
                    attempts += OverpassAttempt(endpoint, attempt.error)
                    val recoverable = attempt.error.isRecoverable
                    Log.w(
                        TAG,
                        "Endpoint $index falhou em ${durationMillis}ms (${attempt.error.diagnostic})" +
                            if (recoverable) ", a tentar o seguinte" else ", sem fallback",
                    )
                    if (!recoverable) return OverpassResult.Failure(attempts)
                }
            }
        }

        Log.w(TAG, "Todos os ${endpoints.size} endpoints falharam")
        return OverpassResult.Failure(attempts)
    }

    private suspend fun attempt(endpoint: String, query: String): Attempt = try {
        val response = api.searchParking(endpoint, query)
        val body = response.body()

        when {
            !response.isSuccessful -> Attempt.Failed(OverpassError.Http(response.code()))

            body == null -> Attempt.Failed(
                OverpassError.Parsing(IllegalStateException("Resposta sem corpo")),
            )

            else -> Attempt.Ok(
                status = response.code(),
                received = body.elements.size,
                elements = body.elements.filter { it.hasCoordinates },
            )
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        currentCoroutineContext().ensureActive()
        Attempt.Failed(failure.toOverpassError())
    }

    private sealed interface Attempt {

        data class Ok(
            val status: Int,
            val received: Int,
            val elements: List<OverpassElement>,
        ) : Attempt

        data class Failed(val error: OverpassError) : Attempt
    }

    companion object {
        private const val TAG = "OverpassClient"
        private const val CONNECT_TIMEOUT_SECONDS = 5L
        private const val CALL_TIMEOUT_SECONDS = 10L

        // Ignorado em runtime porque cada chamada usa @Url, mas exigido pelo Retrofit.
        private const val BASE_URL = "https://overpass-api.de/"

        fun create(endpoints: List<String> = OverpassEndpoints.DEFAULT): OverpassClient =
            OverpassClient(api = createApi(), endpoints = endpoints)

        fun createApi(): OverpassApi = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(createHttpClient())
            .addConverterFactory(overpassJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OverpassApi::class.java)

        private fun createHttpClient(): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build()
                    chain.proceed(request)
                }

            // A query contem a posicao do utilizador, por isso so se regista em debug.
            if (BuildConfig.DEBUG) {
                builder.addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    },
                )
            }

            return builder.build()
        }

        private val USER_AGENT: String
            get() = "VccParking/${BuildConfig.VERSION_NAME} (Android)"
    }
}
