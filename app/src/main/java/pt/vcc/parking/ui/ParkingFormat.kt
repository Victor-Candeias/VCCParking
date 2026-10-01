package pt.vcc.parking.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Currency
import kotlin.math.roundToInt
import pt.vcc.parking.R
import pt.vcc.parking.domain.OpeningHours
import pt.vcc.parking.domain.ParkingCharge
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

// vp-12-rich-details

/**
 * Estado de abertura em texto.
 *
 * `Unknown` devolve o valor em bruto do OSM e nunca uma afirmacao: o utilizador
 * le `Mo-Fr 08:00-20:00` sem dificuldade, e e preferivel a app admitir que nao
 * interpretou a dizer «aberto» a quem vai encontrar o parque fechado.
 */
@Composable
fun openingStateLabel(
    openingHours: String?,
    now: LocalDateTime = LocalDateTime.now(),
): String? = when (val state = OpeningHours.parse(openingHours, now)) {
    is OpeningHours.AlwaysOpen -> stringResource(R.string.parking_open_always)

    is OpeningHours.Open -> state.closesAt
        ?.let { stringResource(R.string.parking_open_until, it.format(HOUR_FORMAT)) }
        ?: stringResource(R.string.parking_open_now)

    is OpeningHours.Closed -> state.opensAt
        ?.let { stringResource(R.string.parking_closed_until, it.format(HOUR_FORMAT)) }
        ?: stringResource(R.string.parking_closed_now)

    is OpeningHours.Unknown -> state.raw
}

/** Preco legivel; o valor monetario respeita a locale do dispositivo. */
@Composable
fun chargeLabel(parking: Parking): String? = when (val charge = ParkingCharge.of(parking)) {
    is ParkingCharge.Free -> stringResource(R.string.parking_fee_no)

    is ParkingCharge.Priced -> {
        val amount = currencyFormat(charge.currency).format(charge.amount)
        charge.unit
            ?.let { stringResource(R.string.parking_charge_per_unit, amount, chargeUnitLabel(it)) }
            ?: amount
    }

    is ParkingCharge.PricedUnknownAmount -> stringResource(R.string.parking_fee_unknown_amount)
    is ParkingCharge.Conditional -> charge.raw
    is ParkingCharge.Unknown -> null
}

/** As unidades mais comuns ganham nome; as restantes ficam como estao. */
@Composable
private fun chargeUnitLabel(unit: String): String = when (unit.trim().lowercase()) {
    "h", "hour" -> stringResource(R.string.parking_charge_unit_hour)
    "day", "d" -> stringResource(R.string.parking_charge_unit_day)
    "month" -> stringResource(R.string.parking_charge_unit_month)
    else -> unit
}

@Composable
fun maxHeightLabel(meters: Double?): String? =
    meters?.let { stringResource(R.string.parking_max_height, it) }

/** `null` nao e «nao»: so o valor explicito produz texto. */
@Composable
fun coveredLabel(covered: Boolean?): String? = when (covered) {
    null -> null
    true -> stringResource(R.string.parking_covered_yes)
    false -> stringResource(R.string.parking_covered_no)
}

@Composable
fun supervisedLabel(supervised: Boolean?): String? = when (supervised) {
    null -> null
    true -> stringResource(R.string.parking_supervised_yes)
    false -> stringResource(R.string.parking_supervised_no)
}

@Composable
fun paymentMethodsLabel(methods: List<String>): String? {
    if (methods.isEmpty()) return null

    // `joinToString` recebe uma lambda normal e nao pode chamar `stringResource`;
    // os nomes sao traduzidos antes de serem juntos.
    val labels = methods.map { paymentMethodLabel(it) }
    return labels.joinToString(separator = ", ")
}

@Composable
private fun paymentMethodLabel(method: String): String = when (method.trim().lowercase()) {
    "cash", "coins", "notes" -> stringResource(R.string.parking_payment_cash)
    "cards", "credit_cards", "debit_cards" -> stringResource(R.string.parking_payment_card)
    "contactless" -> stringResource(R.string.parking_payment_contactless)
    "app" -> stringResource(R.string.parking_payment_app)
    // Os valores do OSM usam `_`; trocar por espaco chega para ficar legivel.
    else -> method.replace('_', ' ')
}

/**
 * Formatador do valor monetario.
 *
 * A locale e a do dispositivo — e quem decide o separador decimal e a posicao
 * do simbolo — mas a moeda vem do OSM: um preco em EUR nao passa a ser em libras
 * por o telemovel estar em ingles britanico.
 */
@Composable
private fun currencyFormat(currencyCode: String): NumberFormat {
    val locale = LocalConfiguration.current.locales[0]

    return remember(locale, currencyCode) {
        NumberFormat.getCurrencyInstance(locale).apply {
            runCatching { currency = Currency.getInstance(currencyCode) }
        }
    }
}

private val HOUR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private const val METERS_PER_KILOMETER = 1_000.0
