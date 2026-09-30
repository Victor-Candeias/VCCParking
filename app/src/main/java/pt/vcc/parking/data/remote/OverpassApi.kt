package pt.vcc.parking.data.remote

import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Cada chamada indica o endpoint completo, para que o mesmo cliente sirva
 * todas as instancias Overpass usadas no fallback.
 */
interface OverpassApi {

    @FormUrlEncoded
    @POST
    suspend fun searchParking(
        @Url endpoint: String,
        @Field("data") query: String,
    ): Response<OverpassResponse>
}
