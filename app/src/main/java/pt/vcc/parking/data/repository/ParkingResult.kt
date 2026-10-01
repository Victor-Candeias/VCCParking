package pt.vcc.parking.data.repository

import pt.vcc.parking.data.remote.OverpassAttempt
import pt.vcc.parking.domain.model.Parking

/**
 * Resultado do repositorio.
 *
 * A seccao 26 exige que a UI saiba quando esta a mostrar cache e a seccao 27
 * exige uma mensagem diferente consoante exista ou nao cache, por isso o
 * resultado responde as duas perguntas em vez de devolver so uma lista.
 */
sealed interface ParkingResult {

    data class Success(
        /** Ja filtrada, com distancia calculada e ordenada (seccao 29). */
        val parking: List<Parking>,
        /** `true` quando os dados vêm da cache e podem estar desatualizados. */
        val fromCache: Boolean,
        /** Epoch millis da gravacao dos dados apresentados. */
        val updatedAtMillis: Long,
    ) : ParkingResult

    /** Nao havia cache utilizavel; as tentativas alimentam a mensagem da seccao 27. */
    data class Failure(
        val attempts: List<OverpassAttempt>,
    ) : ParkingResult
}
