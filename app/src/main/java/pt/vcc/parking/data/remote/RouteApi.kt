package pt.vcc.parking.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * Cada chamada indica o URL completo, para que o mesmo cliente sirva todas as
 * instancias de routing usadas no fallback — tal como [OverpassApi].
 */
interface RouteApi {

    @GET
    suspend fun route(@Url url: String): Response<RouteResponse>
}
