package pt.vcc.parking.ui.parked

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.vcc.parking.R

/**
 * Accao «Estacionei aqui» do ecra principal (`vp-08-park-save`).
 *
 * O botao fica desativado enquanto a posicao esta a ser capturada: a captura
 * pode demorar ate dez segundos e varios toques criariam registos repetidos,
 * terminando uns aos outros.
 */
@Composable
fun ParkHereButton(
    capturing: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = !capturing,
    ) {
        if (capturing) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Text(stringResource(R.string.parked_action_park_here))
        }
    }
}
