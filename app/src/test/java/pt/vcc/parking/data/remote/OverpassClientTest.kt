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

class OverpassClientTest {

    @Test
    fun `returns the first successful endpoint and drops elements without coordinates`() = runBlocking {
        val api = FakeOverpassApi(listOf(success(NODE, WAY_WITHOUT_CENTER)))
        val client = client(api, ENDPOINTS.take(1))

        val result = client.searchParking(LATITUDE, LONGITUDE)

        val success = result as OverpassResult.Success
        assertEquals(ENDPOINT_A, success.endpoint)
        assertEquals(listOf(1L), success.elements.map { it.id })
        assertEquals(listOf(ENDPOINT_A), api.calls)
    }

    @Test
    fun `falls back to the next endpoint on a gateway timeout`() = runBlocking {
        val api = FakeOverpassApi(listOf(httpError(504), success(NODE)))
        val client = client(api)

        val result = client.searchParking(LATITUDE, LONGITUDE)

        val success = result as OverpassResult.Success
        assertEquals(ENDPOINT_B, success.endpoint)
        assertEquals(listOf(ENDPOINT_A, ENDPOINT_B), api.calls)
    }

    @Test
    fun `falls back on timeout rate limiting and parsing errors`() = runBlocking {
        val api = FakeOverpassApi(
            listOf(
                failure(java.net.SocketTimeoutException("timeout")),
                httpError(429),
                failure(SerializationException("nao e json")),
            ),
        )
        val client = client(api)

        val result = client.searchParking(LATITUDE, LONGITUDE)

        val failure = result as OverpassResult.Failure
        assertEquals(ENDPOINTS, api.calls)
        assertEquals(ENDPOINTS, failure.attempts.map { it.endpoint })
        assertEquals(OverpassError.Timeout, failure.attempts[0].error)
        assertEquals(OverpassError.Http(429), failure.attempts[1].error)
        assertTrue(failure.attempts[2].error is OverpassError.Parsing)
    }

    @Test
    fun `falls back when the server closes the connection`() = runBlocking {
        val api = FakeOverpassApi(listOf(failure(java.io.IOException("rede")), success(NODE)))
        val client = client(api)

        val result = client.searchParking(LATITUDE, LONGITUDE)

        assertEquals(ENDPOINT_B, (result as OverpassResult.Success).endpoint)
        assertEquals(listOf(ENDPOINT_A, ENDPOINT_B), api.calls)
    }

    @Test
    fun `stops immediately when the query itself is rejected`() = runBlocking {
        val api = FakeOverpassApi(listOf(httpError(400), success(NODE)))
        val client = client(api)

        val result = client.searchParking(LATITUDE, LONGITUDE)

        val failure = result as OverpassResult.Failure
        assertEquals(listOf(ENDPOINT_A), api.calls)
        assertEquals(OverpassError.Http(400), failure.attempts.single().error)
    }

    @Test
    fun `treats an empty body as a parsing failure`() = runBlocking {
        val api = FakeOverpassApi(listOf({ Response.success<OverpassResponse>(null) }, success(NODE)))
        val client = client(api)

        val result = client.searchParking(LATITUDE, LONGITUDE)

        assertEquals(ENDPOINT_B, (result as OverpassResult.Success).endpoint)
    }

    @Test
    fun `propagates coroutine cancellation`() {
        val api = FakeOverpassApi(listOf(failure(CancellationException("cancelado"))))
        val client = client(api)

        assertThrows(CancellationException::class.java) {
            runBlocking { client.searchParking(LATITUDE, LONGITUDE) }
        }

        assertEquals(listOf(ENDPOINT_A), api.calls)
    }

    @Test
    fun `rejects an empty endpoint list`() {
        assertThrows(IllegalArgumentException::class.java) {
            OverpassClient(FakeOverpassApi(emptyList()), emptyList())
        }
    }

    private fun client(api: OverpassApi, endpoints: List<String> = ENDPOINTS) =
        OverpassClient(api = api, endpoints = endpoints, elapsedMillis = { 0L })

    private fun success(vararg elements: OverpassElement): () -> Response<OverpassResponse> =
        { Response.success(OverpassResponse(elements.toList())) }

    private fun httpError(code: Int): () -> Response<OverpassResponse> = {
        Response.error(code, "".toResponseBody("application/json".toMediaType()))
    }

    private fun failure(error: Throwable): () -> Response<OverpassResponse> = { throw error }

    private class FakeOverpassApi(
        private val outcomes: List<() -> Response<OverpassResponse>>,
    ) : OverpassApi {

        val calls = mutableListOf<String>()

        override suspend fun searchParking(
            endpoint: String,
            query: String,
        ): Response<OverpassResponse> {
            val outcome = outcomes[calls.size]
            calls += endpoint
            return outcome()
        }
    }

    private companion object {
        const val ENDPOINT_A = "https://a.example/api/interpreter"
        const val ENDPOINT_B = "https://b.example/api/interpreter"
        const val ENDPOINT_C = "https://c.example/api/interpreter"
        val ENDPOINTS = listOf(ENDPOINT_A, ENDPOINT_B, ENDPOINT_C)

        const val LATITUDE = 38.7253
        const val LONGITUDE = -9.1500

        val NODE = OverpassElement(
            type = "node",
            id = 1L,
            lat = 38.7253,
            lon = -9.1500,
            tags = mapOf("amenity" to "parking"),
        )

        val WAY_WITHOUT_CENTER = OverpassElement(type = "way", id = 2L)
    }
}
