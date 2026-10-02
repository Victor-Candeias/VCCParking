package pt.vcc.parking.car

import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.model.Distance
import androidx.car.app.model.DistanceSpan
import java.util.Locale
import java.util.concurrent.TimeUnit
import pt.vcc.parking.R
import pt.vcc.parking.domain.model.Parking

/**
 * Texto dos templates projetados (`vp-16-android-auto`).
 *
 * O `ParkingFormat` do telemovel nao serve aqui: as suas funcoes sao
 * `@Composable` e o carro nao corre Compose. Os recursos de texto sao os mesmos,
 * para que as duas interfaces digam exatamente o mesmo ao utilizador.
 */

internal fun CarContext.parkingTitle(parking: Parking): String =
    parking.name ?: getString(R.string.parking_unnamed)

/**
 * A distancia viaja como [DistanceSpan] e nao como texto ja formatado.
 *
 * E o host que conhece as definicoes do veiculo: o mesmo valor aparece em
 * quilometros ou em milhas sem a app ter de decidir nada.
 */
internal fun distanceSpan(meters: Double): CharSequence =
    SpannableString(DISTANCE_PLACEHOLDER).apply {
        setSpan(
            DistanceSpan.create(meters.toDistance()),
            0,
            DISTANCE_PLACEHOLDER.length,
            Spanned.SPAN_INCLUSIVE_INCLUSIVE,
        )
    }

/**
 * Linha secundaria da lista: distancia e, a seguir, o que decide a paragem.
 *
 * Devolve `null` quando nao ha nada a dizer, para a linha nao ficar com um
 * espaco vazio por baixo do nome.
 */
internal fun CarContext.parkingSubtitle(parking: Parking): CharSequence? {
    val summary = parkingSummary(parking)
    val distance = parking.distanceMeters ?: return summary.ifEmpty { null }

    val builder = SpannableStringBuilder(distanceSpan(distance))
    if (summary.isNotEmpty()) builder.append(SEPARATOR).append(summary)
    return builder
}

/** Capacidade e preco, os dois unicos dados que mudam a decisao em andamento. */
internal fun CarContext.parkingSummary(parking: Parking): String = listOfNotNull(
    parking.capacity?.let { getString(R.string.parking_capacity, it) },
    carFeeLabel(parking),
).joinToString(SEPARATOR)

/**
 * Preco em texto curto.
 *
 * Mantem a regra da seccao 24 que atravessa a app: sem tag, nada e afirmado.
 * `charge` e preferido a `fee` por ser mais concreto — «1,20 €/h» responde
 * melhor do que «Pago».
 */
internal fun CarContext.carFeeLabel(parking: Parking): String? {
    parking.charge?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

    return when (parking.isFree) {
        true -> getString(R.string.parking_fee_no)
        false -> getString(R.string.parking_fee_yes)
        null -> null
    }
}

/** Horario tal como esta no OSM; interpreta-lo exigiria o calendario completo. */
internal fun openingHoursLabel(parking: Parking): String? =
    parking.openingHours?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Ha quanto tempo o carro esta estacionado, com a granularidade que interessa a
 * quem o vai buscar: minutos na primeira hora, depois horas e dias.
 */
internal fun CarContext.parkedSinceLabel(parkedAtMillis: Long, nowMillis: Long): String {
    val elapsed = (nowMillis - parkedAtMillis).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
    val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
    val days = TimeUnit.MILLISECONDS.toDays(elapsed)

    return when {
        minutes < 1 -> getString(R.string.parked_since_now)
        hours < 1 -> resources.getQuantityString(
            R.plurals.parked_since_minutes,
            minutes.toInt(),
            minutes.toInt(),
        )

        days < 1 -> resources.getQuantityString(
            R.plurals.parked_since_hours,
            hours.toInt(),
            hours.toInt(),
        )

        else -> resources.getQuantityString(
            R.plurals.parked_since_days,
            days.toInt(),
            days.toInt(),
        )
    }
}

private fun Double.toDistance(): Distance =
    if (this < METERS_PER_KILOMETER) {
        Distance.create(this, Distance.UNIT_METERS)
    } else {
        Distance.create(this / METERS_PER_KILOMETER, Distance.UNIT_KILOMETERS)
    }

/**
 * Coordenadas para o esquema `geo:`.
 *
 * A locale e sempre a raiz: uma virgula decimal produziria um URI que nenhuma
 * app de navegacao interpreta — a mesma razao que levou `ExternalNavigation` a
 * fixar a locale no telemovel.
 */
internal fun carCoordinates(latitude: Double, longitude: Double): String = String.format(
    Locale.ROOT,
    "%.6f,%.6f",
    latitude,
    longitude,
)

/** O `DistanceSpan` substitui o conteudo, mas precisa de caracteres para o fazer. */
private const val DISTANCE_PLACEHOLDER = "  "

private const val SEPARATOR = " · "

private const val METERS_PER_KILOMETER = 1_000.0
