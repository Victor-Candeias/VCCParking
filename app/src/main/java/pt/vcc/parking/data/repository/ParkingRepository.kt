package pt.vcc.parking.data.repository

import android.util.Log
import java.util.concurrent.TimeUnit
import pt.vcc.parking.data.local.ParkingCache
import pt.vcc.parking.data.remote.OverpassQuery
import pt.vcc.parking.data.remote.OverpassResult
import pt.vcc.parking.data.remote.OverpassService
import pt.vcc.parking.data.remote.toParking
import pt.vcc.parking.domain.nearestFrom
import pt.vcc.parking.location.UserLocation

/**
 * Junta a camada remota de `vp-03-overpass` a cache local, conforme a seccao 16.
 *
 * O `OverpassClient` devolve `OverpassResult.Failure` em vez de lancar excecoes
 * precisamente para que a decisao entre cache e erro seja tomada aqui.
 * `CancellationException` continua a propagar: o cliente ja a reemite e este
 * repositorio nao a captura.
 */
class ParkingRepository(
    private val remote: OverpassService,
    private val cache: ParkingCache,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * Devolve os parques perto da posicao pedida.
     *
     * Com cache mais recente do que [FRESHNESS_WINDOW_MILLIS] nem contacta o
     * Overpass (seccao 18). [forceRefresh] ignora essa janela e serve o botao
     * «Pesquisar nesta area» da seccao 19.
     */
    suspend fun parkingNear(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = OverpassQuery.DEFAULT_RADIUS_METERS,
        forceRefresh: Boolean = false,
    ): ParkingResult {
        val cachedAt = cache.latestUpdateAt(latitude, longitude, radiusMeters)

        if (!forceRefresh && cachedAt != null && now() - cachedAt < FRESHNESS_WINDOW_MILLIS) {
            Log.i(TAG, "Cache com ${minutesSince(cachedAt)} min, sem chamada ao Overpass")
            return fromCache(latitude, longitude, radiusMeters, cachedAt)
        }

        return when (val result = remote.searchParking(latitude, longitude, radiusMeters)) {
            is OverpassResult.Success -> {
                val converted = result.elements.toParking()
                val savedAt = now()
                cache.save(converted, savedAt)
                cache.deleteOlderThan(savedAt - RETENTION_MILLIS)

                val visible = converted.nearestFrom(latitude, longitude)
                Log.i(
                    TAG,
                    "Overpass (${result.endpoint}): ${converted.size} parques, " +
                        "${visible.size} apos o filtro",
                )
                ParkingResult.Success(visible, fromCache = false, updatedAtMillis = savedAt)
            }

            is OverpassResult.Failure -> {
                if (cachedAt == null) {
                    Log.w(TAG, "Overpass indisponivel e sem cache utilizavel")
                    ParkingResult.Failure(result.attempts)
                } else {
                    Log.w(
                        TAG,
                        "Overpass indisponivel, a usar cache com ${minutesSince(cachedAt)} min",
                    )
                    fromCache(latitude, longitude, radiusMeters, cachedAt)
                }
            }
        }
    }

    suspend fun parkingNear(
        location: UserLocation,
        radiusMeters: Int = OverpassQuery.DEFAULT_RADIUS_METERS,
        forceRefresh: Boolean = false,
    ): ParkingResult = parkingNear(
        latitude = location.latitude,
        longitude = location.longitude,
        radiusMeters = radiusMeters,
        forceRefresh = forceRefresh,
    )

    /**
     * O filtro da seccao 10 e aplicado na leitura, tanto da rede como da cache,
     * para que os dois caminhos deem o mesmo resultado. A cache guarda tambem os
     * privados: mudar a politica de filtragem nao deve obrigar a limpa-la.
     */
    private suspend fun fromCache(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        updatedAtMillis: Long,
    ): ParkingResult {
        val cached = cache.nearby(latitude, longitude, radiusMeters)
        val visible = cached.nearestFrom(latitude, longitude)
        Log.i(TAG, "Cache: ${cached.size} parques, ${visible.size} apos o filtro")
        return ParkingResult.Success(
            parking = visible,
            fromCache = true,
            updatedAtMillis = updatedAtMillis,
        )
    }

    private fun minutesSince(millis: Long): Long =
        TimeUnit.MILLISECONDS.toMinutes(now() - millis)

    companion object {
        private const val TAG = "ParkingRepository"

        /** Seccao 18: os dados OSM nao representam ocupacao em tempo real. */
        val FRESHNESS_WINDOW_MILLIS: Long = TimeUnit.MINUTES.toMillis(15)

        /** Impede o crescimento indefinido sem apagar cache util offline. */
        val RETENTION_MILLIS: Long = TimeUnit.DAYS.toMillis(7)
    }
}
