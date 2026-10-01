package pt.vcc.parking.data.remote

import pt.vcc.parking.domain.model.Parking

/** Chaves das tags OSM da seccao 9 usadas pelo modelo da seccao 8. */
private object OsmTag {
    const val NAME = "name"
    const val PARKING = "parking"
    const val CAPACITY = "capacity"
    const val OPERATOR = "operator"
    const val ACCESS = "access"
    const val FEE = "fee"
    const val OPENING_HOURS = "opening_hours"
    const val DISABLED_CAPACITY = "capacity:disabled"
    const val ZONE = "zone"
    const val ZONE_COLOUR = "zone:colour"
    const val PHONE = "phone"
    const val CONTACT_PHONE = "contact:phone"
    const val WEBSITE = "website"
    const val CONTACT_WEBSITE = "contact:website"

    // vp-12-rich-details
    const val CHARGE = "charge"
    const val FEE_CONDITIONAL = "fee:conditional"
    const val MAX_HEIGHT = "maxheight"
    const val MAX_STAY = "maxstay"
    const val CONDITION = "parking:condition"
    const val SUPERVISED = "supervised"
    const val SURVEILLANCE = "surveillance"
    const val COVERED = "covered"
    const val CHARGING_CAPACITY = "capacity:charging"
    const val PARENT_CAPACITY = "capacity:parent"
    const val PAYMENT_PREFIX = "payment:"
}

/**
 * Converte um elemento Overpass no modelo interno.
 *
 * Devolve `null` para elementos sem coordenadas utilizaveis: um `way` ou uma
 * `relation` sem `center` nao pode ser colocada no mapa nem medida.
 */
fun OverpassElement.toParking(): Parking? {
    val elementLatitude = latitude ?: return null
    val elementLongitude = longitude ?: return null
    if (!elementLatitude.isFinite() || elementLatitude !in -90.0..90.0) return null
    if (!elementLongitude.isFinite() || elementLongitude !in -180.0..180.0) return null

    return Parking(
        osmId = id,
        osmType = type,
        latitude = elementLatitude,
        longitude = elementLongitude,
        name = tags.text(OsmTag.NAME),
        parkingType = tags.text(OsmTag.PARKING),
        capacity = tags.count(OsmTag.CAPACITY),
        operator = tags.text(OsmTag.OPERATOR),
        access = tags.text(OsmTag.ACCESS),
        fee = tags.text(OsmTag.FEE),
        openingHours = tags.text(OsmTag.OPENING_HOURS),
        disabledCapacity = tags.count(OsmTag.DISABLED_CAPACITY),
        zone = tags.text(OsmTag.ZONE),
        zoneColour = tags.text(OsmTag.ZONE_COLOUR),
        phone = tags.text(OsmTag.PHONE) ?: tags.text(OsmTag.CONTACT_PHONE),
        website = tags.text(OsmTag.WEBSITE) ?: tags.text(OsmTag.CONTACT_WEBSITE),
        charge = tags.text(OsmTag.CHARGE),
        feeConditional = tags.text(OsmTag.FEE_CONDITIONAL),
        maxHeightMeters = tags.heightMeters(OsmTag.MAX_HEIGHT),
        maxStay = tags.text(OsmTag.MAX_STAY),
        condition = tags.text(OsmTag.CONDITION),
        // `surveillance` e vigilancia por camara e `supervised` e por pessoas;
        // para quem deixa o carro as duas respondem a mesma pergunta.
        supervised = tags.flag(OsmTag.SUPERVISED) ?: tags.flag(OsmTag.SURVEILLANCE),
        covered = tags.flag(OsmTag.COVERED),
        chargingCapacity = tags.count(OsmTag.CHARGING_CAPACITY),
        parentCapacity = tags.count(OsmTag.PARENT_CAPACITY),
        paymentMethods = tags.paymentMethods(),
    )
}

fun List<OverpassElement>.toParking(): List<Parking> = mapNotNull { it.toParking() }

private fun Map<String, String>.text(key: String): String? =
    this[key]?.trim()?.takeIf { it.isNotEmpty() }

// `capacity=yes` e valores negativos sao informacao ausente, nao zero lugares.
private fun Map<String, String>.count(key: String): Int? =
    text(key)?.toIntOrNull()?.takeIf { it >= 0 }

/**
 * Tag booleana do OSM; `null` para qualquer outra coisa.
 *
 * `surveillance=camera` e `supervised=interval` sao respostas validas no OSM mas
 * nao sao «sim» nem «nao»; devolver `null` deixa a UI mostrar o valor em bruto
 * em vez de afirmar o que nao sabe.
 */
private fun Map<String, String>.flag(key: String): Boolean? =
    when (text(key)?.lowercase()) {
        "yes" -> true
        "no" -> false
        else -> null
    }

/**
 * Altura em metros a partir de `maxheight`.
 *
 * O OSM aceita `2.1`, `2.1 m` e tambem `yes`, que significa «ha limite mas nao
 * se sabe qual» — inutil para decidir se a carrinha passa, logo `null`. Pes e
 * polegadas (`6'6"`) ficam de fora: sao raros na Europa e um valor convertido
 * ao contrario era pior do que valor nenhum.
 */
private fun Map<String, String>.heightMeters(key: String): Double? {
    val raw = text(key)?.lowercase() ?: return null
    val number = raw.removeSuffix("m").trim().toDoubleOrNull() ?: return null
    return number.takeIf { it.isFinite() && it > 0.0 }
}

/**
 * Meios de pagamento aceites, a partir das tags `payment:*` com valor `yes`.
 *
 * Um `payment:cash=no` e informacao sobre o que nao e aceite e nao entra na
 * lista: a pergunta que a UI responde e «com que posso pagar».
 */
private fun Map<String, String>.paymentMethods(): List<String> =
    entries
        .filter { (key, _) -> key.startsWith(OsmTag.PAYMENT_PREFIX) }
        .filter { (_, value) -> value.trim().equals("yes", ignoreCase = true) }
        .map { (key, _) -> key.removePrefix(OsmTag.PAYMENT_PREFIX) }
        .filter { it.isNotEmpty() }
        .sorted()
