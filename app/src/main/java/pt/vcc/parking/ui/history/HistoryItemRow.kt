package pt.vcc.parking.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import pt.vcc.parking.R
import pt.vcc.parking.domain.model.ParkedCar
import pt.vcc.parking.history.durationMillis
import pt.vcc.parking.ui.parked.ParkedCarPhoto

/**
 * Linha do historico (`vp-10-history`).
 *
 * Mostra o intervalo e a duracao, que e o que responde a pergunta de quem revê
 * os registos — «quanto tempo estive ali» —, e nao as coordenadas, que so
 * dizem alguma coisa no mapa do detalhe.
 */
@Composable
fun HistoryItemRow(
    entry: ParkedCar,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entry.photoUri?.let { photoUri ->
            ParkedCarPhoto(
                photoUri = photoUri,
                modifier = Modifier
                    .size(THUMBNAIL_SIZE)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.history_entry_period,
                    clockLabel(entry.parkedAtMillis),
                    entry.endedAtMillis?.let { clockLabel(it) }.orEmpty(),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )

            Text(
                text = historyDurationLabel(entry),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            entry.note?.let { note ->
                Text(text = note, style = MaterialTheme.typography.bodyMedium)
            }
        }

        TextButton(onClick = onDelete) {
            Text(stringResource(R.string.history_action_delete))
        }
    }
}

/** Cabecalho de dia: uma lista plana de centenas de linhas nao se le. */
@Composable
internal fun HistoryDayHeader(dayMillis: Long, modifier: Modifier = Modifier) {
    Text(
        text = historyDayLabel(dayMillis),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * «Hoje» e «Ontem» por extenso: num registo do proprio dia, a data completa
 * obriga a traduzir mentalmente o que o utilizador ja sabe.
 */
@Composable
internal fun historyDayLabel(millis: Long): String = when (daysSince(millis)) {
    0L -> stringResource(R.string.history_day_today)
    1L -> stringResource(R.string.history_day_yesterday)
    else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
}

/** Minutos ate uma hora, depois horas, depois dias: a unidade segue a escala. */
@Composable
internal fun historyDurationLabel(entry: ParkedCar): String {
    val millis = entry.durationMillis ?: 0L
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val days = TimeUnit.MILLISECONDS.toDays(millis)

    return when {
        hours < 1 -> stringResource(R.string.history_duration_minutes, minutes)
        days < 1 -> stringResource(
            R.string.history_duration_hours,
            hours,
            minutes % MINUTES_PER_HOUR,
        )

        else -> stringResource(R.string.history_duration_days, days, hours % HOURS_PER_DAY)
    }
}

/** Inicio do dia do registo; e a chave que agrupa a lista. */
internal fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

internal fun clockLabel(millis: Long): String =
    SimpleDateFormat(CLOCK_PATTERN, Locale.getDefault()).format(Date(millis))

private fun daysSince(millis: Long): Long =
    TimeUnit.MILLISECONDS.toDays(startOfDay(System.currentTimeMillis()) - startOfDay(millis))

private const val CLOCK_PATTERN = "HH:mm"
private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L
private val THUMBNAIL_SIZE = 56.dp
