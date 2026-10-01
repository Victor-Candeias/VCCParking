package pt.vcc.parking.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt
import pt.vcc.parking.R
import pt.vcc.parking.domain.model.Parking

/**
 * Traducao das tags OSM da seccao 9 para texto apresentavel.
 *
 * A seccao 24 proibe inventar informacao: um valor desconhecido e mostrado tal
 * como esta no OSM, em vez de ser descartado ou interpretado por adivinhacao.
 */

/** Seccao 23: metros ate 1 km, depois quilometros com uma casa decimal. */
@Composable
fun distanceLabel(meters: Double?): String = when {
    meters == null -> stringResource(R.string.parking_distance_unknown)
    meters < METERS_PER_KILOMETER -> stringResource(
        R.string.parking_distance_meters,
        meters.roundToInt(),
    )

    else -> stringResource(
        R.string.parking_distance_kilometers,
        meters / METERS_PER_KILOMETER,
    )
}

@Composable
fun radiusLabel(radiusMeters: Int): String =
    if (radiusMeters < METERS_PER_KILOMETER) {
        stringResource(R.string.parking_radius_meters, radiusMeters)
    } else {
        stringResource(R.string.parking_radius_kilometers, radiusMeters / 1_000)
    }

@Composable
fun parkingName(parking: Parking): String =
    parking.name ?: stringResource(R.string.parking_unnamed)

/** `fee` so e booleano em `yes`/`no`; `interval` e `donation` ficam em bruto. */
@Composable
fun feeLabel(fee: String?): String? = when (fee?.trim()?.lowercase()) {
    null -> null
    "yes" -> stringResource(R.string.parking_fee_yes)
    "no" -> stringResource(R.string.parking_fee_no)
    else -> fee
}

@Composable
fun accessLabel(access: String?): String? = when (access?.trim()?.lowercase()) {
    null -> null
    "yes", "public" -> stringResource(R.string.parking_access_public)
    "customers" -> stringResource(R.string.parking_access_customers)
    "permissive" -> stringResource(R.string.parking_access_permissive)
    "destination" -> stringResource(R.string.parking_access_destination)
    "permit" -> stringResource(R.string.parking_access_permit)
    else -> access
}

@Composable
fun parkingTypeLabel(parkingType: String?): String? = when (parkingType?.trim()?.lowercase()) {
    null -> null
    "surface" -> stringResource(R.string.parking_type_surface)
    "multi-storey" -> stringResource(R.string.parking_type_multi_storey)
    "underground" -> stringResource(R.string.parking_type_underground)
    "rooftop" -> stringResource(R.string.parking_type_rooftop)
    "street_side" -> stringResource(R.string.parking_type_street_side)
    "lane" -> stringResource(R.string.parking_type_lane)
    "layby" -> stringResource(R.string.parking_type_layby)
    "carports" -> stringResource(R.string.parking_type_carports)
    "garage_boxes" -> stringResource(R.string.parking_type_garage_boxes)
    "sheds" -> stringResource(R.string.parking_type_sheds)
    else -> parkingType
}

@Composable
fun capacityLabel(capacity: Int?): String? =
    capacity?.let { stringResource(R.string.parking_capacity, it) }

/** A zona so e util com o nome; a cor acompanha-a quando existe. */
@Composable
fun zoneLabel(parking: Parking): String? {
    val zone = parking.zone ?: return null
    val colour = parking.zoneColour ?: return zone
    return stringResource(R.string.parking_zone_with_colour, zone, colour)
}

private const val METERS_PER_KILOMETER = 1_000.0
