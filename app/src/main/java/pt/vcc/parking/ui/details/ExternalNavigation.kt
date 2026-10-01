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
fun Context.openExternalNavigation(parking: Parking): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, parking.toGeoUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    return try {
        startActivity(intent)
        true
    } catch (notFound: ActivityNotFoundException) {
        Log.w(TAG, "Sem aplicacao para o esquema geo:", notFound)
        false
    }
}

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

// Uma locale com virgula decimal produziria um URI que nenhum mapa interpreta.
private fun Double.asCoordinate(): String = String.format(Locale.ROOT, "%.6f", this)
