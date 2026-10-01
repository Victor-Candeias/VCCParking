package pt.vcc.parking.ui.reminder

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.concurrent.TimeUnit
import pt.vcc.parking.R

/**
 * Antecedencia do aviso e lembrete periodico (`vp-11-reminders`).
 *
 * O periodico vem desligado por omissao: quem nao o pediu nao deve comecar a
 * receber notificacoes de hora a hora, porque a reaccao previsivel e desligar o
 * canal inteiro e perder tambem o aviso do prazo.
 */
@Composable
fun ReminderSettingsSection(
    warnBeforeMillis: Long,
    recurringEveryMillis: Long?,
    modifier: Modifier = Modifier,
    deadlineEnabled: Boolean = true,
    onWarnBeforeSelected: (Long) -> Unit = {},
    onRecurringSelected: (Long?) -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Sem prazo nao ha nada que possa ser antecipado; mostrar a opcao
        // sugeriria um aviso que nunca chegaria.
        if (deadlineEnabled) {
            Text(
                text = stringResource(R.string.reminder_warn_before_label),
                style = MaterialTheme.typography.titleSmall,
            )

            ChipRow {
                WARN_BEFORE_OPTIONS_MINUTES.forEach { minutes ->
                    val millis = TimeUnit.MINUTES.toMillis(minutes)
                    FilterChip(
                        selected = millis == warnBeforeMillis,
                        onClick = { onWarnBeforeSelected(millis) },
                        label = {
                            Text(
                                stringResource(
                                    R.string.reminder_warn_before_option,
                                    minutes.toInt(),
                                ),
                            )
                        },
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.reminder_recurring_label),
            style = MaterialTheme.typography.titleSmall,
        )

        ChipRow {
            FilterChip(
                selected = recurringEveryMillis == null,
                onClick = { onRecurringSelected(null) },
                label = { Text(stringResource(R.string.reminder_recurring_off)) },
            )

            RECURRING_OPTIONS_HOURS.forEach { hours ->
                val millis = TimeUnit.HOURS.toMillis(hours)
                FilterChip(
                    selected = millis == recurringEveryMillis,
                    onClick = { onRecurringSelected(millis) },
                    label = {
                        Text(stringResource(R.string.reminder_recurring_option, hours.toInt()))
                    },
                )
            }
        }
    }
}

/** Mesma linha rolavel dos raios de pesquisa, para o ecra nao mudar de gramatica. */
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

/** Menos de 5 min nao da tempo de reagir; mais de 30 avisa cedo demais. */
internal val WARN_BEFORE_OPTIONS_MINUTES = listOf(5L, 10L, 15L, 30L)

internal val RECURRING_OPTIONS_HOURS = listOf(2L, 6L, 24L)
