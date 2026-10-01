package pt.vcc.parking.ui.filters

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pt.vcc.parking.R
import pt.vcc.parking.domain.ParkingFilter
import pt.vcc.parking.ui.theme.VccParkingTheme

/**
 * Atalhos para os filtros mais usados, com o resto atras de «Filtros».
 *
 * Os dois chips a vista respondem as perguntas mais frequentes — «e gratis?» e
 * «esta aberto?» — e evitam abrir uma folha inteira por um unico toque.
 */
@Composable
fun ParkingFilterBar(
    filter: ParkingFilter,
    modifier: Modifier = Modifier,
    onFilterChanged: (ParkingFilter) -> Unit = {},
    onOpenFilters: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = onOpenFilters,
            label = {
                Text(
                    text = if (filter.isActive) {
                        stringResource(R.string.parking_filter_active_count, filter.activeCount)
                    } else {
                        stringResource(R.string.parking_filter_action)
                    },
                )
            },
        )

        FilterChip(
            selected = filter.freeOnly,
            onClick = { onFilterChanged(filter.copy(freeOnly = !filter.freeOnly)) },
            label = { Text(stringResource(R.string.parking_badge_free)) },
        )

        FilterChip(
            selected = filter.openNowOnly,
            onClick = { onFilterChanged(filter.copy(openNowOnly = !filter.openNowOnly)) },
            label = { Text(stringResource(R.string.parking_badge_open)) },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ParkingFilterBarPreview() {
    VccParkingTheme {
        ParkingFilterBar(filter = ParkingFilter(freeOnly = true))
    }
}
