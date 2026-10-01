package pt.vcc.parking.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import pt.vcc.parking.R
import pt.vcc.parking.domain.FilteredParking
import pt.vcc.parking.domain.OpeningHours
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
    ParkingList(
        filtered = FilteredParking(matching = parking),
        modifier = modifier,
        onParkingSelected = onParkingSelected,
    )
}

/**
 * Lista com a seccao dos parques sem informacao suficiente
 * (`vp-12-rich-details`).
 *
 * Os incertos ficam no fim e com explicacao, em vez de escondidos: a app nao
 * pode deixar o utilizador a pensar que nao existe mais nada por perto so
 * porque o OSM nao tem as tags que o filtro precisa.
 */
@Composable
fun ParkingList(
    filtered: FilteredParking,
    modifier: Modifier = Modifier,
    onParkingSelected: (Parking) -> Unit = {},
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(items = filtered.matching, key = { it.id }) { item ->
            ParkingListItem(
                parking = item,
                onClick = { onParkingSelected(item) },
            )
            HorizontalDivider()
        }

        if (filtered.matching.isEmpty()) {
            item(key = EMPTY_MATCHES_KEY) {
                Text(
                    text = stringResource(R.string.parking_filter_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                )
            }
        }

        if (filtered.unknown.isNotEmpty()) {
            item(key = UNKNOWN_HEADER_KEY) {
                UnknownHeader(count = filtered.unknown.size)
            }

            items(items = filtered.unknown, key = { "unknown-${it.id}" }) { item ->
                ParkingListItem(
                    parking = item,
                    onClick = { onParkingSelected(item) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun UnknownHeader(count: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.parking_filter_unknown_header, count),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.parking_filter_unknown_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

        ParkingBadges(parking = parking)
    }
}

/**
 * Indicadores de `vp-12-rich-details`.
 *
 * So aparecem com tag explicita: um parque sem `covered` nao recebe indicador
 * nenhum — nem de coberto nem de descoberto — porque a ausencia da tag nao diz
 * nada sobre o parque.
 */
@Composable
private fun ParkingBadges(parking: Parking, modifier: Modifier = Modifier) {
    val badges = buildList {
        if (parking.isFree == true) add(stringResource(R.string.parking_badge_free))

        when (OpeningHours.parse(parking.openingHours, LocalDateTime.now())) {
            is OpeningHours.AlwaysOpen, is OpeningHours.Open ->
                add(stringResource(R.string.parking_badge_open))

            is OpeningHours.Closed -> add(stringResource(R.string.parking_badge_closed))
            is OpeningHours.Unknown -> Unit
        }

        if (parking.covered == true) add(stringResource(R.string.parking_badge_covered))
        if (parking.hasDisabledSpaces == true) {
            add(stringResource(R.string.parking_badge_disabled))
        }
        if (parking.hasChargingSpaces == true) {
            add(stringResource(R.string.parking_badge_charging))
        }
    }

    if (badges.isEmpty()) return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        badges.forEach { badge ->
            // Desativado de proposito: e um rotulo, nao uma accao.
            SuggestionChip(
                onClick = {},
                enabled = false,
                label = {
                    Text(text = badge, style = MaterialTheme.typography.labelSmall)
                },
                border = AssistChipDefaults.assistChipBorder(enabled = false),
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

private const val EMPTY_MATCHES_KEY = "empty-matches"
private const val UNKNOWN_HEADER_KEY = "unknown-header"

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
                    openingHours = "24/7",
                    covered = true,
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
