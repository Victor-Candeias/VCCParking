package pt.vcc.parking.ui.details

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import pt.vcc.parking.R
import pt.vcc.parking.domain.model.Parking
import pt.vcc.parking.ui.theme.VccParkingTheme

/**
 * Convite a corrigir o parque no OpenStreetMap (`vp-12-rich-details`).
 *
 * Aparece sempre, e nao so quando faltam dados: a app nao tem forma de saber se
 * o que esta no OSM esta certo, e quem acabou de visitar o parque e a unica
 * pessoa em posicao de o confirmar.
 */
@Composable
fun ContributeToOsm(
    parking: Parking,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.parking_contribute_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.parking_contribute_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { context.openOsmEditor(parking) }) {
            Text(stringResource(R.string.parking_contribute_action))
        }
    }
}

/**
 * Abre o editor do OSM ja centrado neste elemento.
 *
 * Devolve `false` quando nao ha navegador instalado, para o ecra poder avisar
 * em vez de nao acontecer nada.
 */
fun Context.openOsmEditor(parking: Parking): Boolean =
    openExternalUrl(osmEditUrl(parking).toUri())

/**
 * Endereco do editor para este elemento.
 *
 * O editor aceita `?node=`, `?way=` ou `?relation=`; um `osmType` fora destes
 * tres nao da um URL valido, por isso cai para a pagina generica de edicao em
 * vez de enviar o utilizador para um erro.
 */
internal fun osmEditUrl(parking: Parking): String {
    val type = parking.osmType.trim().lowercase()
    return if (type in EDITABLE_OSM_TYPES) {
        "$OSM_EDIT_URL?$type=${parking.osmId}"
    } else {
        OSM_EDIT_URL
    }
}

private const val OSM_EDIT_URL = "https://www.openstreetmap.org/edit"

private val EDITABLE_OSM_TYPES = setOf("node", "way", "relation")

@Preview(showBackground = true)
@Composable
private fun ContributeToOsmPreview() {
    VccParkingTheme {
        ContributeToOsm(
            parking = Parking(
                osmId = 1,
                osmType = "way",
                latitude = 38.7336,
                longitude = -9.1447,
            ),
        )
    }
}
