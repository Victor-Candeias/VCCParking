package pt.vcc.parking.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pt.vcc.parking.R
import pt.vcc.parking.domain.model.Parking
import pt.vcc.parking.ui.accessLabel
import pt.vcc.parking.ui.capacityLabel
import pt.vcc.parking.ui.distanceLabel
import pt.vcc.parking.ui.feeLabel
import pt.vcc.parking.ui.parkingName
import pt.vcc.parking.ui.parkingTypeLabel
import pt.vcc.parking.ui.theme.VccParkingTheme
import pt.vcc.parking.ui.zoneLabel

/**
 * Detalhe da seccao 24 do documento do MVP.
 *
 * Cada linha so aparece quando a tag existe: a seccao 9 e explicita em que uma
 * tag ausente significa informacao indisponivel, e a seccao 24 proibe inventar
 * dados que o OSM nao tem.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkingDetailsSheet(
    parking: Parking,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    onNavigate: (Parking) -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(),
    ) {
        ParkingDetails(
            parking = parking,
            onNavigate = onNavigate,
        )
    }
}

@Composable
private fun ParkingDetails(
    parking: Parking,
    modifier: Modifier = Modifier,
    onNavigate: (Parking) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = parkingName(parking),
            style = MaterialTheme.typography.headlineSmall,
        )

        DetailRow(
            label = stringResource(R.string.parking_label_distance),
            value = distanceLabel(parking.distanceMeters),
        )
        DetailRow(
            label = stringResource(R.string.parking_label_capacity),
            value = capacityLabel(parking.capacity),
        )
        DetailRow(
            label = stringResource(R.string.parking_label_type),
            value = parkingTypeLabel(parking.parkingType),
        )
        DetailRow(
            label = stringResource(R.string.parking_label_operator),
            value = parking.operator,
        )
        DetailRow(
            label = stringResource(R.string.parking_label_access),
            value = accessLabel(parking.access),
        )
        DetailRow(
            label = stringResource(R.string.parking_label_fee),
            value = feeLabel(parking.fee),
        )
        DetailRow(
            label = stringResource(R.string.parking_label_opening_hours),
            value = parking.openingHours,
        )
        DetailRow(
            label = stringResource(R.string.parking_label_disabled_capacity),
            value = capacityLabel(parking.disabledCapacity),
        )
        DetailRow(
            label = stringResource(R.string.parking_label_zone),
            value = zoneLabel(parking),
        )
        DetailRow(
            label = stringResource(R.string.parking_label_phone),
            value = parking.phone,
        )
        DetailRow(
            label = stringResource(R.string.parking_label_website),
            value = parking.website,
        )

        Button(
            onClick = { onNavigate(parking) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.parking_action_navigate))
        }
    }
}

/** Um valor a `null` nao produz linha nenhuma. */
@Composable
private fun DetailRow(label: String, value: String?) {
    if (value == null) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ParkingDetailsPreview() {
    VccParkingTheme {
        ParkingDetails(
            parking = Parking(
                osmId = 1,
                osmType = "way",
                latitude = 38.7336,
                longitude = -9.1447,
                name = "Parque Saldanha Residence",
                parkingType = "underground",
                capacity = 120,
                operator = "Empark",
                access = "yes",
                fee = "yes",
                openingHours = "24/7",
                disabledCapacity = 6,
                zone = "Verde",
                zoneColour = "green",
                phone = "+351 210 000 000",
                website = "https://example.org",
                distanceMeters = 350.0,
            ),
        )
    }
}
