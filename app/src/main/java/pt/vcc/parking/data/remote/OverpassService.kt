package pt.vcc.parking.data.remote

/**
 * Contrato da camada remota. O repositorio de `vp-05-cache` depende desta
 * interface e nao da implementacao Retrofit.
 */
interface OverpassService {

    suspend fun searchParking(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = OverpassQuery.DEFAULT_RADIUS_METERS,
    ): OverpassResult
}
