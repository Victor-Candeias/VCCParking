package pt.vcc.parking.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.vcc.parking.data.local.ParkedCarDao
import pt.vcc.parking.data.local.ParkedCarEntity
import pt.vcc.parking.data.local.toParkedCar
import pt.vcc.parking.data.local.toParkedCars
import pt.vcc.parking.domain.model.ParkedCar
import pt.vcc.parking.domain.model.Parking

/**
 * Regras do estacionamento ativo (`vp-08-park-save`).
 *
 * O relogio entra por construtor, como em `vp-05-cache`, para que os testes
 * nao dependam da hora real da maquina.
 */
class ParkedCarRepository(
    private val dao: ParkedCarDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    /** `null` quando nao ha carro estacionado; a UI reage sem fazer polling. */
    fun observeActive(): Flow<ParkedCar?> = dao.observeActive().map { it?.toParkedCar() }

    /** Todos os registos, do mais recente para o mais antigo (base de `vp-10-history`). */
    fun observeAll(): Flow<List<ParkedCar>> = dao.observeAll().map { it.toParkedCars() }

    suspend fun active(): ParkedCar? = dao.active()?.toParkedCar()

    /**
     * Guarda uma nova posicao, terminando a anterior na mesma transacao.
     *
     * [parking] apenas liga o registo ao parque escolhido; as coordenadas sao
     * sempre as recebidas, porque o lugar onde o carro ficou nao coincide com o
     * ponto que o OSM usa para representar o parque inteiro.
     */
    suspend fun park(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float? = null,
        note: String? = null,
        photoUri: String? = null,
        parking: Parking? = null,
    ): ParkedCar {
        val parkedAtMillis = now()
        val entity = ParkedCarEntity(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            parkedAtMillis = parkedAtMillis,
            note = note.normalized(),
            photoUri = photoUri.normalized(),
            osmType = parking?.osmType,
            osmId = parking?.osmId,
        )
        val id = dao.park(entity)
        return entity.copy(id = id).toParkedCar()
    }

    /** Edicao posterior; nao volta a capturar a posicao nem mexe nas datas. */
    suspend fun updateDetails(id: Long, note: String?, photoUri: String?): ParkedCar? {
        dao.updateDetails(id = id, note = note.normalized(), photoUri = photoUri.normalized())
        return dao.byId(id)?.toParkedCar()
    }

    /**
     * Corrige a posicao de um registo existente.
     *
     * E uma edicao e nao um novo estacionamento: o carro nao mudou de sitio,
     * mudou a ideia que a app tinha dele. Criar outro registo deixaria no
     * historico uma paragem que nunca aconteceu.
     */
    suspend fun updatePosition(id: Long, latitude: Double, longitude: Double): ParkedCar? {
        dao.updatePosition(id = id, latitude = latitude, longitude = longitude)
        return dao.byId(id)?.toParkedCar()
    }

    /**
     * Termina o estacionamento ativo. Nao apaga nada: o registo passa a fazer
     * parte do historico.
     *
     * Devolve `false` quando nao havia nada ativo, para a UI nao anunciar uma
     * accao que nao chegou a acontecer.
     */
    suspend fun endActive(): Boolean = dao.endActive(now()) > 0

    /** Texto vazio e ausencia de informacao, e a tabela guarda isso como `null`. */
    private fun String?.normalized(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
