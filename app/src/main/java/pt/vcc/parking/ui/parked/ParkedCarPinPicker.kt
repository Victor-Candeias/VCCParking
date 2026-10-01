package pt.vcc.parking.ui.parked

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Image
import pt.vcc.parking.R
import pt.vcc.parking.ui.map.MapPoint
import pt.vcc.parking.ui.map.ParkingMap

/**
 * Marcacao manual do ponto onde o carro ficou (`vp-08-park-save`).
 *
 * E a saida para o caso que o GPS nao resolve — cave de parque, interior de
 * centro comercial — e por isso nao e opcional: sem ela a funcionalidade falha
 * exatamente onde e mais precisa.
 *
 * O pino fica fixo no centro do ecra e e o mapa que se move por baixo. Arrastar
 * um marcador com o dedo tapa-o justamente quando e preciso ve-lo.
 */
@Composable
fun ParkedCarPinPicker(
    initialLatitude: Double,
    initialLongitude: Double,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    onConfirm: (latitude: Double, longitude: Double) -> Unit = { _, _ -> },
) {
    var picked by remember(initialLatitude, initialLongitude) {
        mutableStateOf(MapPoint(initialLatitude, initialLongitude))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                ParkingMap(
                    userLocation = null,
                    parking = emptyList(),
                    modifier = Modifier.fillMaxSize(),
                    initialCenter = MapPoint(initialLatitude, initialLongitude),
                    onCenterChanged = { latitude, longitude ->
                        picked = MapPoint(latitude, longitude)
                    },
                )

                // A ponta do pino aponta para o centro exato do mapa; o desenho
                // e subido em metade da altura para a ponta ficar no centro.
                Image(
                    painter = painterResource(R.drawable.ic_map_car),
                    contentDescription = stringResource(R.string.parked_marker),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(PIN_SIZE)
                        .offset(y = -PIN_SIZE / 2),
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                ) {
                    Text(
                        text = stringResource(R.string.parked_pin_title),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(16.dp),
                ) {
                    Button(
                        onClick = { onConfirm(picked.latitude, picked.longitude) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.parked_pin_confirm))
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.parked_action_cancel))
                    }
                }
            }
        }
    }
}

private val PIN_SIZE = 48.dp
