package pt.vcc.parking.data.remote

/**
 * Instancias de routing usadas pelo fallback, pela ordem em que sao tentadas.
 *
 * A escolha segue o criterio de [OverpassEndpoints]: sem chave de API, para que
 * nenhuma credencial pessoal viaje com o pedido. A primeira e a unica com perfil
 * pedonal real; a instancia de demonstracao do OSRM so conhece o perfil de
 * automovel e serve apenas para nao deixar o utilizador sem linha nenhuma.
 */
object RouteEndpoints {

    val DEFAULT: List<String> = listOf(
        "https://routing.openstreetmap.de/routed-foot",
        "https://router.project-osrm.org",
    )
}
