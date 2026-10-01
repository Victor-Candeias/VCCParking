package pt.vcc.parking.ui.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.vcc.parking.R
import pt.vcc.parking.data.local.HistoryRetention
import pt.vcc.parking.history.HistoryExportFormat

/**
 * Retencao, exportacao e remocao total (`vp-10-history`).
 *
 * Fica no fim da propria lista em vez de um ecra de definicoes a parte: sao
 * decisoes sobre os registos que estao mesmo ali por cima, e esconde-las noutro
 * sitio seria esconder justamente o controlo que o utilizador tem sobre eles.
 */
@Composable
fun HistoryPrivacySection(
    retention: HistoryRetention,
    modifier: Modifier = Modifier,
    exporting: Boolean = false,
    canExport: Boolean = true,
    onRetentionSelected: (HistoryRetention) -> Unit = {},
    onExport: (HistoryExportFormat) -> Unit = {},
    onDeleteAll: () -> Unit = {},
) {
    var confirmingClear by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider()

        Text(
            text = stringResource(R.string.history_privacy_title),
            style = MaterialTheme.typography.titleMedium,
        )

        Text(
            text = stringResource(R.string.history_privacy_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = stringResource(R.string.history_retention_title),
            style = MaterialTheme.typography.titleSmall,
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HistoryRetention.entries.forEach { option ->
                FilterChip(
                    selected = option == retention,
                    onClick = { onRetentionSelected(option) },
                    label = { Text(retentionLabel(option)) },
                )
            }
        }

        Text(
            text = stringResource(R.string.history_export_title),
            style = MaterialTheme.typography.titleSmall,
        )

        if (exporting) {
            Text(
                text = stringResource(R.string.history_exporting),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryExportFormat.entries.forEach { format ->
                OutlinedButton(
                    onClick = { onExport(format) },
                    enabled = canExport && !exporting,
                ) {
                    Text(exportLabel(format))
                }
            }
        }

        TextButton(
            onClick = { confirmingClear = true },
            enabled = canExport,
        ) {
            Text(
                text = stringResource(R.string.history_clear_action),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    // Apagar tudo nao tem anulacao possivel, por isso a confirmacao e explicita
    // e diz o que acontece ao estacionamento que ainda esta a decorrer.
    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingClear = false
                        onDeleteAll()
                    },
                ) {
                    Text(stringResource(R.string.history_clear_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClear = false }) {
                    Text(stringResource(R.string.history_action_cancel))
                }
            },
            title = { Text(stringResource(R.string.history_clear_confirm_title)) },
            text = { Text(stringResource(R.string.history_clear_confirm_message)) },
        )
    }
}

@Composable
private fun retentionLabel(retention: HistoryRetention): String = stringResource(
    when (retention) {
        HistoryRetention.Days30 -> R.string.history_retention_days_30
        HistoryRetention.Days90 -> R.string.history_retention_days_90
        HistoryRetention.Year -> R.string.history_retention_year
        HistoryRetention.Forever -> R.string.history_retention_forever
    },
)

@Composable
private fun exportLabel(format: HistoryExportFormat): String = stringResource(
    when (format) {
        HistoryExportFormat.GeoJson -> R.string.history_export_geojson
        HistoryExportFormat.Csv -> R.string.history_export_csv
    },
)
