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
    )
}

fun List<OverpassElement>.toParking(): List<Parking> = mapNotNull { it.toParking() }

private fun Map<String, String>.text(key: String): String? =
    this[key]?.trim()?.takeIf { it.isNotEmpty() }

// `capacity=yes` e valores negativos sao informacao ausente, nao zero lugares.
private fun Map<String, String>.count(key: String): Int? =
    text(key)?.toIntOrNull()?.takeIf { it >= 0 }
