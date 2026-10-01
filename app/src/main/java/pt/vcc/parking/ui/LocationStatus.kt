package pt.vcc.parking.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pt.vcc.parking.R
import pt.vcc.parking.location.LocationUiState
import pt.vcc.parking.ui.theme.VccParkingTheme

/**
 * Estados de localizacao de `vp-02-location` quando ainda nao ha posicao.
 *
 * Sem coordenadas nao ha pesquisa possivel (seccao 29), por isso este ecra
 * substitui o mapa em vez de o acompanhar. A seccao 13 exige explicar porque e
 * que a permissao e necessaria e deixar caminho para as definicoes.
 */
@Composable
fun LocationStatus(
    uiState: LocationUiState,
    modifier: Modifier = Modifier,
    onRequestPermission: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    onOpenLocationSettings: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (uiState) {
            LocationUiState.Idle -> {
                Message(stringResource(R.string.location_idle))
                Action(R.string.location_action_refresh, onRefresh)
            }

            LocationUiState.Loading -> {
                CircularProgressIndicator()
                Message(stringResource(R.string.location_loading))
            }

            LocationUiState.PermissionRequired -> {
                Message(stringResource(R.string.location_permission_rationale))
                Action(R.string.location_action_allow, onRequestPermission)
            }

            LocationUiState.PermissionDenied -> {
                Message(stringResource(R.string.location_permission_denied))
                Action(R.string.location_action_settings, onOpenAppSettings)
            }

            LocationUiState.LocationDisabled -> {
                Message(stringResource(R.string.location_disabled))
                Action(R.string.location_action_location_settings, onOpenLocationSettings)
            }

            LocationUiState.Unavailable -> {
                Message(stringResource(R.string.location_unavailable))
                Action(R.string.location_action_retry, onRefresh)
            }

            // Com posicao disponivel o ecra principal mostra o mapa.
            is LocationUiState.Available -> Unit
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Action(textResourceId: Int, onClick: () -> Unit) {
    Button(onClick = onClick) {
        Text(stringResource(textResourceId))
    }
}

@Preview(showBackground = true)
@Composable
private fun LocationStatusPermissionPreview() {
    VccParkingTheme {
        LocationStatus(uiState = LocationUiState.PermissionRequired)
    }
}
