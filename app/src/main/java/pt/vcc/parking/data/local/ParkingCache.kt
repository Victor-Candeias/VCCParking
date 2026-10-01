package pt.vcc.parking.data.local

import pt.vcc.parking.domain.model.Parking

/**
 * Contrato da cache usado pelo repositorio da seccao 16.
 *
 * O repositorio depende desta interface e nao do DAO por um motivo pratico: o
 * Room exige contexto Android e nao funciona nos testes unitarios JVM, pelo que
 * os testes do repositorio usam uma implementacao falsa — o mesmo padrao com
 * que `vp-03-overpass` isolou o `OverpassService`.
 */
interface ParkingCache {

    /** Parques guardados na area pedida, sem distancia calculada. */
    suspend fun nearby(latitude: Double, longitude: Double, radiusMeters: Int): List<Parking>

    /** Epoch millis da gravacao mais recente na area, ou `null` se nao houver cache. */
    suspend fun latestUpdateAt(latitude: Double, longitude: Double, radiusMeters: Int): Long?

    suspend fun save(parking: List<Parking>, updatedAtMillis: Long)

    suspend fun deleteOlderThan(thresholdMillis: Long)
}
