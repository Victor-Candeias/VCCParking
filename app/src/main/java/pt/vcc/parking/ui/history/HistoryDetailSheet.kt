package pt.vcc.parking.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.vcc.parking.R
import pt.vcc.parking.domain.model.ParkedCar
import pt.vcc.parking.ui.map.MapPoint
import pt.vcc.parking.ui.map.ParkingMap
import pt.vcc.parking.ui.parked.ParkedCarPhoto

/**
 * Detalhe de um registo do historico (`vp-10-history`).
 *
 * Reutiliza o mapa de `vp-06-ui` com o marcador do carro de `vp-08-park-save`:
 * sem parques a mostrar e sem posicao do utilizador, porque aqui o que
 * interessa e onde o carro esteve, nao onde o utilizador esta agora.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailSheet(
    entry: ParkedCar,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.history_detail_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            Text(
                text = historyDayLabel(entry.parkedAtMillis),
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                text = stringResource(
                    R.string.history_entry_period,
                    clockLabel(entry.parkedAtMillis),
                    entry.endedAtMillis?.let { clockLabel(it) }.orEmpty(),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )

            Text(
                text = stringResource(
                    R.string.history_detail_duration,
                    historyDurationLabel(entry),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            entry.note?.let { note ->
                Text(text = note, style = MaterialTheme.typography.bodyMedium)
            }

            ParkingMap(
                userLocation = null,
                parking = emptyList(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MAP_HEIGHT)
                    .clip(RoundedCornerShape(12.dp)),
                parkedCar = entry,
                initialCenter = MapPoint(entry.latitude, entry.longitude),
            )

            Text(
                text = stringResource(
                    R.string.history_detail_coordinates,
                    entry.latitude,
                    entry.longitude,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            entry.photoUri?.let { photoUri ->
                ParkedCarPhoto(
                    photoUri = photoUri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PHOTO_HEIGHT)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }

            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.history_action_delete))
            }
        }
    }
}

private val MAP_HEIGHT = 220.dp
private val PHOTO_HEIGHT = 180.dp
