package pt.vcc.parking.ui.details

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import java.util.Locale
import pt.vcc.parking.domain.model.Parking

private const val TAG = "ExternalNavigation"

/**
 * Abre a navegacao numa aplicacao externa, conforme a seccao 25 do documento do
 * MVP: o MVP nao implementa navegacao propria.
 *
 * Devolve `false` quando nao existe aplicacao capaz de tratar o esquema `geo:`,
 * para que o ecra possa avisar o utilizador em vez de rebentar.
 */
fun Context.openExternalNavigation(parking: Parking): Boolean = start(parking.toGeoUri())

/**
 * O `q=` garante que o destino fica marcado e nao apenas centrado. O nome segue
 * entre parenteses como etiqueta e e codificado porque pode conter espacos.
 */
private fun Parking.toGeoUri(): Uri {
    val latitude = latitude.asCoordinate()
    val longitude = longitude.asCoordinate()
    val query = buildString {
        append("geo:$latitude,$longitude?q=$latitude,$longitude")
        name?.let { append("(${Uri.encode(it)})") }
    }
    return query.toUri()
}

/**
 * Abre a navegacao a pe ate ao carro (`vp-09-return-route`).
 *
 * O esquema `geo:` nao tem forma de pedir um modo de transporte, por isso e
 * tentado primeiro o `google.navigation:` com `mode=w`, que o percebe. Sem
 * aplicacao que o trate, a queda para `geo:` continua a levar o utilizador ao
 * ponto certo — apenas com o modo por omissao.
 */
fun Context.openWalkingNavigation(
    latitude: Double,
    longitude: Double,
    label: String? = null,
): Boolean {
    val coordinates = "${latitude.asCoordinate()},${longitude.asCoordinate()}"

    val walking = "google.navigation:q=$coordinates&mode=w".toUri()
    val fallback = buildString {
        append("geo:$coordinates?q=$coordinates")
        label?.let { append("(${Uri.encode(it)})") }
    }.toUri()

    return start(walking) || start(fallback)
}

private fun Context.start(uri: Uri): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    return try {
        startActivity(intent)
        true
    } catch (notFound: ActivityNotFoundException) {
        Log.w(TAG, "Sem aplicacao para o esquema ${uri.scheme}", notFound)
        false
    }
}

// Uma locale com virgula decimal produziria um URI que nenhum mapa interpreta.
private fun Double.asCoordinate(): String = String.format(Locale.ROOT, "%.6f", this)
