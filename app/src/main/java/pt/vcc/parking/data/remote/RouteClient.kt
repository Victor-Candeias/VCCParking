package pt.vcc.parking.data.remote

import android.os.SystemClock
import android.util.Log
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import pt.vcc.parking.BuildConfig
import pt.vcc.parking.domain.model.WalkingRoute
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Percorre as instancias de routing por ordem e devolve a primeira rota valida
 * (`vp-09-return-route`), com a mesma estrategia de fallback do [OverpassClient].
 *
 * Nao lanca excecoes de rede para fora: devolve [RouteResult.Failure] para que o
 * repositorio possa degradar para o modo bussola sem inspecionar excecoes.
 */
class RouteClient(
    private val api: RouteApi,
    private val endpoints: List<String> = RouteEndpoints.DEFAULT,
    private val elapsedMillis: () -> Long = SystemClock::elapsedRealtime,
) : RouteService {

    init {
        require(endpoints.isNotEmpty()) { "E necessario pelo menos um endpoint de routing" }
    }

    override suspend fun walkingRoute(
        startLatitude: Double,
        startLongitude: Double,
        endLatitude: Double,
        endLongitude: Double,
    ): RouteResult {
        val attempts = mutableListOf<RouteAttempt>()

        for ((index, endpoint) in endpoints.withIndex()) {
            val url = buildWalkingUrl(
                endpoint = endpoint,
                startLatitude = startLatitude,
                startLongitude = startLongitude,
                endLatitude = endLatitude,
                endLongitude = endLongitude,
            )

            val startedAt = elapsedMillis()
            val attempt = attempt(url)
            val durationMillis = elapsedMillis() - startedAt

            when (attempt) {
                is Attempt.Ok -> {
                    Log.i(TAG, "Instancia $index respondeu em ${durationMillis}ms com rota")
                    return RouteResult.Success(endpoint, attempt.route)
                }

                Attempt.NoRoute -> {
                    Log.i(TAG, "Instancia $index respondeu em ${durationMillis}ms sem caminho a pe")
                    return RouteResult.Empty
                }

                is Attempt.Failed -> {
                    attempts += RouteAttempt(endpoint, attempt.error)
                    val recoverable = attempt.error.isRecoverable
                    Log.w(
                        TAG,
                        "Instancia $index falhou em ${durationMillis}ms (${attempt.error.diagnostic})" +
                            if (recoverable) ", a tentar a seguinte" else ", sem fallback",
                    )
                    if (!recoverable) return RouteResult.Failure(attempts)
                }
            }
        }

        Log.w(TAG, "Todas as ${endpoints.size} instancias de routing falharam")
        return RouteResult.Failure(attempts)
    }

    private suspend fun attempt(url: String): Attempt = try {
        val response = api.route(url)
        val body = response.body()

        when {
            !response.isSuccessful -> Attempt.Failed(RouteError.Http(response.code()))

            body == null -> Attempt.Failed(
                RouteError.Parsing(IllegalStateException("Resposta sem corpo")),
            )

            else -> body.toWalkingRoute()?.let(Attempt::Ok) ?: Attempt.NoRoute
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        currentCoroutineContext().ensureActive()
        Attempt.Failed(failure.toRouteError())
    }

    private sealed interface Attempt {

        data class Ok(val route: WalkingRoute) : Attempt

        data object NoRoute : Attempt

        data class Failed(val error: RouteError) : Attempt
    }

    companion object {
        private const val TAG = "RouteClient"

        /** A pe nao ha urgencia, mas um ecra parado mais do que isto parece avariado. */
        private const val CONNECT_TIMEOUT_SECONDS = 5L
        private const val CALL_TIMEOUT_SECONDS = 10L

        // Ignorado em runtime porque cada chamada usa @Url, mas exigido pelo Retrofit.
        private const val BASE_URL = "https://routing.openstreetmap.de/"

        /**
         * O OSRM recebe as coordenadas como `longitude,latitude` — a ordem
         * inversa da usada no resto da app — e separa origem e destino por `;`.
         */
        internal fun buildWalkingUrl(
            endpoint: String,
            startLatitude: Double,
            startLongitude: Double,
            endLatitude: Double,
            endLongitude: Double,
        ): String {
            val start = "${startLongitude.asCoordinate()},${startLatitude.asCoordinate()}"
            val end = "${endLongitude.asCoordinate()},${endLatitude.asCoordinate()}"
            return "${endpoint.trimEnd('/')}/route/v1/foot/$start;$end" +
                "?overview=full&geometries=geojson&alternatives=false&steps=false"
        }

        fun create(endpoints: List<String> = RouteEndpoints.DEFAULT): RouteClient =
            RouteClient(api = createApi(), endpoints = endpoints)

        fun createApi(): RouteApi = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(createHttpClient())
            .addConverterFactory(routeJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RouteApi::class.java)

        /**
         * Sem interceptor de registo, nem em debug: o URL leva a posicao do
         * utilizador e a do carro, e e isso que o risco de privacidade da spec
         * manda manter fora dos logs.
         */
        private fun createHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .build()

        private val USER_AGENT: String
            get() = "VccParking/${BuildConfig.VERSION_NAME} (Android)"

        // Uma locale com virgula decimal produziria um URL que o OSRM rejeita.
        private fun Double.asCoordinate(): String = String.format(Locale.ROOT, "%.6f", this)
    }
}
