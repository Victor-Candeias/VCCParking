package pt.vcc.parking.data.remote

/**
 * Instancias publicas usadas pelo fallback, pela ordem em que sao tentadas.
 *
 * A seccao 5 do documento do MVP avisa que estes endpoints mudam, por isso a
 * lista fica isolada e pode ser substituida por construtor.
 */
object OverpassEndpoints {

    val DEFAULT: List<String> = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    )
}
