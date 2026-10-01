package pt.vcc.parking.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pt.vcc.parking.R
import pt.vcc.parking.domain.model.Parking
import pt.vcc.parking.ui.capacityLabel
import pt.vcc.parking.ui.distanceLabel
import pt.vcc.parking.ui.feeLabel
import pt.vcc.parking.ui.parkingName
import pt.vcc.parking.ui.parkingTypeLabel
import pt.vcc.parking.ui.theme.VccParkingTheme

/**
 * Lista da seccao 23 do documento do MVP, ja ordenada por distancia crescente
 * pelo `ParkingViewModel`.
 */
@Composable
fun ParkingList(
    parking: List<Parking>,
    modifier: Modifier = Modifier,
    onParkingSelected: (Parking) -> Unit = {},
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(items = parking, key = { it.id }) { item ->
            ParkingListItem(
                parking = item,
                onClick = { onParkingSelected(item) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ParkingListItem(
    parking: Parking,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = parkingName(parking),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = distanceLabel(parking.distanceMeters),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Text(
            text = summaryLine(parking),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        capacityLabel(parking.capacity)?.let { capacity ->
            Text(
                text = capacity,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Seccao 23: quando nao ha preco nem tipo, o item diz que a informacao nao existe
 * em vez de ficar com uma linha vazia ou com um valor inventado.
 */
@Composable
private fun summaryLine(parking: Parking): String {
    val parts = listOfNotNull(
        feeLabel(parking.fee),
        parkingTypeLabel(parking.parkingType),
    )
    return if (parts.isEmpty()) {
        stringResource(R.string.parking_fee_unknown)
    } else {
        parts.joinToString(separator = " · ")
    }
}

@Preview(showBackground = true)
@Composable
private fun ParkingListPreview() {
    VccParkingTheme {
        ParkingList(
            parking = listOf(
                Parking(
                    osmId = 1,
                    osmType = "way",
                    latitude = 38.7336,
                    longitude = -9.1447,
                    name = "Parque Saldanha Residence",
                    parkingType = "underground",
                    capacity = 120,
                    fee = "yes",
                    distanceMeters = 350.0,
                ),
                Parking(
                    osmId = 2,
                    osmType = "node",
                    latitude = 38.7301,
                    longitude = -9.1502,
                    distanceMeters = 620.0,
                ),
            ),
        )
    }
}
