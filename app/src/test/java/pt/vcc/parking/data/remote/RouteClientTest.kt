package pt.vcc.parking.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class RouteClientTest {

    @Test
    fun `returns the route from the first endpoint that answers`() = runBlocking {
        val api = FakeRouteApi(listOf(success(ROUTE)))
        val client = client(api, ENDPOINTS.take(1))

        val result = client.walkingRoute(START_LATITUDE, START_LONGITUDE, END_LATITUDE, END_LONGITUDE)

        val success = result as RouteResult.Success
        assertEquals(ENDPOINT_A, success.endpoint)
        assertEquals(420.0, success.route.distanceMeters, 0.0)
        assertEquals(listOf(38.7253, 38.7300), success.route.points.map { it.latitude })
        assertEquals(1, api.calls.size)
    }

    @Test
    fun `falls back to the next instance on a gateway timeout`() = runBlocking {
        val api = FakeRouteApi(listOf(httpError(504), success(ROUTE)))
        val client = client(api)

        val result = client.walkingRoute(START_LATITUDE, START_LONGITUDE, END_LATITUDE, END_LONGITUDE)

        assertEquals(ENDPOINT_B, (result as RouteResult.Success).endpoint)
        assertEquals(2, api.calls.size)
    }

    @Test
    fun `falls back on timeout rate limiting and parsing errors`() = runBlocking {
        val api = FakeRouteApi(
            listOf(
                failure(java.net.SocketTimeoutException("timeout")),
                httpError(429),
                failure(SerializationException("nao e json")),
            ),
        )
        val client = client(api)

        val result = client.walkingRoute(START_LATITUDE, START_LONGITUDE, END_LATITUDE, END_LONGITUDE)

        val failure = result as RouteResult.Failure
        assertEquals(ENDPOINTS, failure.attempts.map { it.endpoint })
        assertEquals(RouteError.Timeout, failure.attempts[0].error)
        assertEquals(RouteError.Http(429), failure.attempts[1].error)
        assertTrue(failure.attempts[2].error is RouteError.Parsing)
    }

    @Test
    fun `stops immediately when the request itself is rejected`() = runBlocking {
        val api = FakeRouteApi(listOf(httpError(400), success(ROUTE)))
        val client = client(api)

        val result = client.walkingRoute(START_LATITUDE, START_LONGITUDE, END_LATITUDE, END_LONGITUDE)

        val failure = result as RouteResult.Failure
        assertEquals(1, api.calls.size)
        assertEquals(RouteError.Http(400), failure.attempts.single().error)
    }

    @Test
    fun `treats an empty body as a parsing failure`() = runBlocking {
        val api = FakeRouteApi(listOf({ Response.success<RouteResponse>(null) }, success(ROUTE)))
        val client = client(api)

        val result = client.walkingRoute(START_LATITUDE, START_LONGITUDE, END_LATITUDE, END_LONGITUDE)

        assertEquals(ENDPOINT_B, (result as RouteResult.Success).endpoint)
    }

    /**
     * Sem caminho a pe nao ha nada a ganhar em perguntar a outra instancia: os
     * dados do OSM sao os mesmos em todas.
     */
    @Test
    fun `a service answering without a path does not trigger the fallback`() = runBlocking {
        val api = FakeRouteApi(listOf(success(RouteResponse(code = "NoRoute")), success(ROUTE)))
        val client = client(api)

        val result = client.walkingRoute(START_LATITUDE, START_LONGITUDE, END_LATITUDE, END_LONGITUDE)

        assertEquals(RouteResult.Empty, result)
        assertEquals(1, api.calls.size)
    }

    @Test
    fun `a geometry with a single point is not a line`() = runBlocking {
        val single = RouteResponse(
            code = "Ok",
            routes = listOf(
                RouteDto(geometry = RouteGeometry(listOf(listOf(-9.1500, 38.7253)))),
            ),
        )
        val client = client(FakeRouteApi(listOf(success(single))))

        val result = client.walkingRoute(START_LATITUDE, START_LONGITUDE, END_LATITUDE, END_LONGITUDE)

        assertEquals(RouteResult.Empty, result)
    }

    @Test
    fun `invalid coordinates are dropped without losing the route`() = runBlocking {
        val noisy = RouteResponse(
            code = "Ok",
            routes = listOf(
                RouteDto(
                    distance = 10.0,
                    geometry = RouteGeometry(
                        listOf(
                            listOf(-9.1500, 38.7253),
                            listOf(-9.1490),
                            listOf(-9.1480, 120.0),
                            listOf(-9.1470, 38.7300),
                        ),
                    ),
                ),
            ),
        )
        val client = client(FakeRouteApi(listOf(success(noisy))))

        val result = client.walkingRoute(START_LATITUDE, START_LONGITUDE, END_LATITUDE, END_LONGITUDE)

        assertEquals(2, (result as RouteResult.Success).route.points.size)
    }

    @Test
    fun `propagates coroutine cancellation`() {
        val api = FakeRouteApi(listOf(failure(CancellationException("cancelado"))))
        val client = client(api)

        assertThrows(CancellationException::class.java) {
            runBlocking {
                client.walkingRoute(START_LATITUDE, START_LONGITUDE, END_LATITUDE, END_LONGITUDE)
            }
        }
    }

    @Test
    fun `rejects an empty endpoint list`() {
        assertThrows(IllegalArgumentException::class.java) {
            RouteClient(FakeRouteApi(emptyList()), emptyList())
        }
    }

    /**
     * O OSRM espera `longitude,latitude` e ponto decimal. Trocar a ordem leva a
     * uma rota noutro continente; a virgula decimal de uma locale europeia parte
     * o URL em campos a mais.
     */
    @Test
    fun `builds a walking url with longitude first and a dot as decimal separator`() {
        val url = RouteClient.buildWalkingUrl(
            endpoint = "$ENDPOINT_A/",
            startLatitude = START_LATITUDE,
            startLongitude = START_LONGITUDE,
            endLatitude = END_LATITUDE,
            endLongitude = END_LONGITUDE,
        )

        assertTrue(url.startsWith("$ENDPOINT_A/route/v1/foot/"))
        assertTrue(url.contains("-9.150000,38.725300;-9.144700,38.733600"))
        assertTrue(url.contains("geometries=geojson"))
    }

    private fun client(api: RouteApi, endpoints: List<String> = ENDPOINTS) =
        RouteClient(api = api, endpoints = endpoints, elapsedMillis = { 0L })

    private fun success(body: RouteResponse): () -> Response<RouteResponse> =
        { Response.success(body) }

    private fun httpError(code: Int): () -> Response<RouteResponse> = {
        Response.error(code, "".toResponseBody("application/json".toMediaType()))
    }

    private fun failure(error: Throwable): () -> Response<RouteResponse> = { throw error }

    private class FakeRouteApi(
        private val outcomes: List<() -> Response<RouteResponse>>,
    ) : RouteApi {

        val calls = mutableListOf<String>()

        override suspend fun route(url: String): Response<RouteResponse> {
            val outcome = outcomes[calls.size]
            calls += url
            return outcome()
        }
    }

    private companion object {
        const val ENDPOINT_A = "https://a.example"
        const val ENDPOINT_B = "https://b.example"
        const val ENDPOINT_C = "https://c.example"
        val ENDPOINTS = listOf(ENDPOINT_A, ENDPOINT_B, ENDPOINT_C)

        const val START_LATITUDE = 38.7253
        const val START_LONGITUDE = -9.1500
        const val END_LATITUDE = 38.7336
        const val END_LONGITUDE = -9.1447

        val ROUTE = RouteResponse(
            code = "Ok",
            routes = listOf(
                RouteDto(
                    distance = 420.0,
                    duration = 330.0,
                    geometry = RouteGeometry(
                        listOf(
                            listOf(-9.1500, 38.7253),
                            listOf(-9.1447, 38.7300),
                        ),
                    ),
                ),
            ),
        )
    }
}
