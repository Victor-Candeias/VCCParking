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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pt.vcc.parking.R
import pt.vcc.parking.domain.ParkingRestrictions
import pt.vcc.parking.domain.model.Parking
import pt.vcc.parking.ui.accessLabel
import pt.vcc.parking.ui.capacityLabel
import pt.vcc.parking.ui.chargeLabel
import pt.vcc.parking.ui.coveredLabel
import pt.vcc.parking.ui.distanceLabel
import pt.vcc.parking.ui.feeLabel
import pt.vcc.parking.ui.maxHeightLabel
import pt.vcc.parking.ui.openingStateLabel
import pt.vcc.parking.ui.parkingName
import pt.vcc.parking.ui.parkingTypeLabel
import pt.vcc.parking.ui.paymentMethodsLabel
import pt.vcc.parking.ui.supervisedLabel
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
    onParkHere: (Parking) -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(),
    ) {
        ParkingDetails(
            parking = parking,
            onNavigate = onNavigate,
            onParkHere = onParkHere,
        )
    }
}

@Composable
private fun ParkingDetails(
    parking: Parking,
    modifier: Modifier = Modifier,
    onNavigate: (Parking) -> Unit = {},
    onParkHere: (Parking) -> Unit = {},
) {
    val restrictions = remember(parking) { ParkingRestrictions.of(parking) }

    // `openingStateLabel` devolve o horario em bruto quando nao o consegue
    // interpretar; mostrar a mesma coisa em duas linhas seguidas so confunde.
    val openingState = openingStateLabel(parking.openingHours)
        ?.takeUnless { it == parking.openingHours }

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
            label = stringResource(R.string.parking_label_zone),
            value = zoneLabel(parking),
        )

        DetailSection(
            title = stringResource(R.string.parking_section_prices),
            rows = listOf(
                stringResource(R.string.parking_label_charge) to
                    (chargeLabel(parking) ?: feeLabel(parking.fee)),
                stringResource(R.string.parking_label_opening_state) to openingState,
                stringResource(R.string.parking_label_opening_hours) to parking.openingHours,
                stringResource(R.string.parking_label_payment) to
                    paymentMethodsLabel(parking.paymentMethods),
            ),
        )

        DetailSection(
            title = stringResource(R.string.parking_section_restrictions),
            rows = listOf(
                stringResource(R.string.parking_label_max_height) to
                    maxHeightLabel(restrictions.maxHeightMeters),
                stringResource(R.string.parking_label_max_stay) to restrictions.maxStay,
                stringResource(R.string.parking_label_condition) to restrictions.condition,
                stringResource(R.string.parking_label_access) to accessLabel(parking.access),
            ),
        )

        DetailSection(
            title = stringResource(R.string.parking_section_facilities),
            rows = listOf(
                stringResource(R.string.parking_label_covered) to coveredLabel(parking.covered),
                stringResource(R.string.parking_label_supervised) to
                    supervisedLabel(parking.supervised),
                stringResource(R.string.parking_label_disabled_capacity) to
                    capacityLabel(parking.disabledCapacity),
                stringResource(R.string.parking_label_charging_capacity) to
                    capacityLabel(parking.chargingCapacity),
                stringResource(R.string.parking_label_parent_capacity) to
                    capacityLabel(parking.parentCapacity),
            ),
        )

        DetailSection(
            title = stringResource(R.string.parking_section_contacts),
            rows = listOf(
                stringResource(R.string.parking_label_operator) to parking.operator,
                stringResource(R.string.parking_label_phone) to parking.phone,
                stringResource(R.string.parking_label_website) to parking.website,
            ),
        )

        Button(
            onClick = { onNavigate(parking) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.parking_action_navigate))
        }

        // Liga o registo a este parque; a posicao guardada continua a ser a do
        // utilizador, porque o ponto do OSM representa o parque inteiro.
        OutlinedButton(
            onClick = { onParkHere(parking) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.parked_action_park_at_parking))
        }

        ContributeToOsm(parking = parking)
    }
}

/**
 * Agrupa linhas sob um titulo e desaparece por inteiro quando nenhuma tem valor.
 *
 * Um titulo sozinho leria como «esta seccao nao se aplica», quando o que se
 * passa e que o OSM nao tem os dados.
 */
@Composable
private fun DetailSection(
    title: String,
    rows: List<Pair<String, String?>>,
    modifier: Modifier = Modifier,
) {
    if (rows.none { it.second != null }) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider()
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        rows.forEach { (label, value) -> DetailRow(label = label, value = value) }
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
                charge = "1.20 EUR/h",
                maxHeightMeters = 1.9,
                maxStay = "4 h",
                covered = true,
                supervised = true,
                chargingCapacity = 4,
                paymentMethods = listOf("cash", "cards"),
            ),
        )
    }
}
