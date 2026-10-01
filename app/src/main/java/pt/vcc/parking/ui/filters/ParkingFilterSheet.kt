package pt.vcc.parking.ui.filters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pt.vcc.parking.R
import pt.vcc.parking.domain.ParkingFilter
import pt.vcc.parking.ui.theme.VccParkingTheme

/**
 * Filtros de `vp-12-rich-details`.
 *
 * Cada interruptor aplica-se de imediato — nao ha botao «aplicar» — porque a
 * lista por tras muda a vista e o utilizador percebe o efeito sem ter de
 * confirmar nada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkingFilterSheet(
    filter: ParkingFilter,
    modifier: Modifier = Modifier,
    onFilterChanged: (ParkingFilter) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(),
    ) {
        ParkingFilterForm(
            filter = filter,
            onFilterChanged = onFilterChanged,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun ParkingFilterForm(
    filter: ParkingFilter,
    modifier: Modifier = Modifier,
    onFilterChanged: (ParkingFilter) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.parking_filter_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        Text(
            text = stringResource(R.string.parking_filter_unknown_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FilterSwitch(
            label = stringResource(R.string.parking_filter_free),
            checked = filter.freeOnly,
            onCheckedChange = { onFilterChanged(filter.copy(freeOnly = it)) },
        )
        FilterSwitch(
            label = stringResource(R.string.parking_filter_open_now),
            checked = filter.openNowOnly,
            onCheckedChange = { onFilterChanged(filter.copy(openNowOnly = it)) },
        )
        FilterSwitch(
            label = stringResource(R.string.parking_filter_covered),
            checked = filter.coveredOnly,
            onCheckedChange = { onFilterChanged(filter.copy(coveredOnly = it)) },
        )
        FilterSwitch(
            label = stringResource(R.string.parking_filter_disabled),
            checked = filter.disabledSpacesOnly,
            onCheckedChange = { onFilterChanged(filter.copy(disabledSpacesOnly = it)) },
        )
        FilterSwitch(
            label = stringResource(R.string.parking_filter_charging),
            checked = filter.chargingSpacesOnly,
            onCheckedChange = { onFilterChanged(filter.copy(chargingSpacesOnly = it)) },
        )

        VehicleHeightField(
            heightMeters = filter.vehicleHeightMeters,
            onHeightChanged = { onFilterChanged(filter.copy(vehicleHeightMeters = it)) },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            if (filter.isActive) {
                TextButton(onClick = { onFilterChanged(filter.cleared()) }) {
                    Text(stringResource(R.string.parking_filter_clear))
                }
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.parking_filter_close))
            }
        }
    }
}

@Composable
private fun FilterSwitch(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Altura do veiculo em metros.
 *
 * O texto escrito fica como esta e so e convertido quando forma um numero
 * valido: apagar o ultimo digito de `1,8` nao pode fazer o campo saltar para
 * outro valor enquanto o utilizador ainda esta a escrever.
 */
@Composable
private fun VehicleHeightField(
    heightMeters: Double?,
    modifier: Modifier = Modifier,
    onHeightChanged: (Double?) -> Unit = {},
) {
    // A chave e «ha ou nao altura» e nao o valor: assim limpar os filtros esvazia
    // o campo, mas escrever nele nao o reescreve a cada tecla.
    var text by remember(heightMeters == null) {
        mutableStateOf(heightMeters?.toString().orEmpty())
    }

    OutlinedTextField(
        value = text,
        onValueChange = { value ->
            text = value
            onHeightChanged(value.toHeightMeters())
        },
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.parking_filter_height)) },
        placeholder = { Text(stringResource(R.string.parking_filter_height_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

/**
 * A virgula e o separador decimal em portugues; o teclado pode dar as duas.
 *
 * Valores nao positivos sao tratados como campo vazio: uma altura de `0` nao
 * filtraria nada e so faria o utilizador pensar que o filtro esta avariado.
 */
internal fun String.toHeightMeters(): Double? =
    trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }

@Preview(showBackground = true)
@Composable
private fun ParkingFilterFormPreview() {
    VccParkingTheme {
        ParkingFilterForm(
            filter = ParkingFilter(freeOnly = true, vehicleHeightMeters = 1.85),
        )
    }
}
