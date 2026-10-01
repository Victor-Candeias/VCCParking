package pt.vcc.parking.ui.returnroute

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pt.vcc.parking.R
import pt.vcc.parking.domain.GeoBearing
import pt.vcc.parking.ui.distanceLabel
import pt.vcc.parking.ui.theme.VccParkingTheme

/**
 * Seta e distancia ate ao carro (`vp-09-return-route`).
 *
 * A distancia aparece sempre; a seta so quando ha bussola. Mostrar uma seta
 * parada num dispositivo sem sensor de rotacao seria pior do que nao a mostrar:
 * continuaria a apontar para um lado qualquer como se fosse o certo.
 */
@Composable
fun ReturnCompass(
    distanceMeters: Double,
    modifier: Modifier = Modifier,
    relativeBearingDegrees: Double? = null,
    arrived: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (relativeBearingDegrees != null && !arrived) {
            ReturnArrow(relativeBearingDegrees = relativeBearingDegrees)
        }

        Text(
            text = distanceLabel(distanceMeters),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )

        Text(
            text = stringResource(
                if (arrived) R.string.return_arriving else R.string.return_distance_to_car,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A seta acumula o caminho curto entre leituras: animar o valor em bruto faria
 * uma volta quase completa ao passar de -179 para 179 graus, que no ecra e um
 * movimento minimo da mao.
 */
@Composable
private fun ReturnArrow(relativeBearingDegrees: Double, modifier: Modifier = Modifier) {
    var accumulated by remember { mutableFloatStateOf(relativeBearingDegrees.toFloat()) }

    LaunchedEffect(relativeBearingDegrees) {
        accumulated += GeoBearing
            .relativeDegrees(relativeBearingDegrees, accumulated.toDouble())
            .toFloat()
    }

    val angle by animateFloatAsState(targetValue = accumulated, label = "returnArrow")

    Image(
        painter = painterResource(R.drawable.ic_return_arrow),
        contentDescription = stringResource(R.string.return_arrow_description),
        modifier = modifier
            .size(ARROW_SIZE)
            .rotate(angle),
    )
}

private val ARROW_SIZE = 96.dp

@Preview(showBackground = true)
@Composable
private fun ReturnCompassPreview() {
    VccParkingTheme {
        ReturnCompass(distanceMeters = 340.0, relativeBearingDegrees = -35.0)
    }
}
