package pt.vcc.parking.ui.reminder

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import java.util.concurrent.TimeUnit
import pt.vcc.parking.R
import pt.vcc.parking.domain.model.ParkingReminder
import pt.vcc.parking.reminder.ReminderUiState

/**
 * Escolha do prazo de estacionamento (`vp-11-reminders`).
 *
 * A permissao de notificacoes e pedida aqui, e nao no arranque: neste momento o
 * utilizador acabou de pedir para ser avisado, pelo que o pedido tem um contexto
 * obvio e a probabilidade de ser aceite e muito maior.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSheet(
    state: ReminderUiState,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    onSave: (durationMillis: Long?, warnBeforeMillis: Long, recurringEveryMillis: Long?) -> Unit =
        { _, _, _ -> },
    onRemove: () -> Unit = {},
    onCapabilitiesChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val reminder = state.reminder
    val initialMinutes = reminder.remainingMinutes()

    // O estado e guardado em tipos simples e nao numa classe selada: o
    // `rememberSaveable` so sabe gravar o que cabe num `Bundle`, e perder a
    // escolha a cada rotacao seria pior do que a verbosidade.
    var selectedMinutes by rememberSaveable(state.parkedCarId) {
        mutableStateOf(initialMinutes?.takeIf { it in DURATION_OPTIONS_MINUTES })
    }
    var customSelected by rememberSaveable(state.parkedCarId) {
        mutableStateOf(initialMinutes != null && initialMinutes !in DURATION_OPTIONS_MINUTES)
    }
    var customMinutes by rememberSaveable(state.parkedCarId) {
        mutableStateOf(if (customSelected) initialMinutes.toString() else "")
    }
    var warnBeforeMillis by rememberSaveable(state.parkedCarId) {
        mutableStateOf(reminder?.warnBeforeMillis ?: ParkingReminder.DEFAULT_WARN_BEFORE_MILLIS)
    }
    var recurringEveryMillis by rememberSaveable(state.parkedCarId) {
        mutableStateOf(reminder?.recurringEveryMillis)
    }

    val hasDeadline = customSelected || selectedMinutes != null
    val customValue = customMinutes.toLongOrNull()
    val canSave = !customSelected || (customValue != null && customValue > 0L)

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onCapabilitiesChanged() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.reminder_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            Text(
                text = stringResource(R.string.reminder_duration_label),
                style = MaterialTheme.typography.titleSmall,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !hasDeadline,
                    onClick = {
                        selectedMinutes = null
                        customSelected = false
                    },
                    label = { Text(stringResource(R.string.reminder_duration_none)) },
                )

                DURATION_OPTIONS_MINUTES.forEach { minutes ->
                    FilterChip(
                        selected = !customSelected && selectedMinutes == minutes,
                        onClick = {
                            selectedMinutes = minutes
                            customSelected = false
                        },
                        label = { Text(durationLabel(minutes)) },
                    )
                }

                FilterChip(
                    selected = customSelected,
                    onClick = {
                        customSelected = true
                        selectedMinutes = null
                    },
                    label = { Text(stringResource(R.string.reminder_duration_custom)) },
                )
            }

            if (customSelected) {
                OutlinedTextField(
                    value = customMinutes,
                    onValueChange = { typed -> customMinutes = typed.filter(Char::isDigit).take(4) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.reminder_custom_minutes_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }

            ReminderSettingsSection(
                warnBeforeMillis = warnBeforeMillis,
                recurringEveryMillis = recurringEveryMillis,
                deadlineEnabled = hasDeadline,
                onWarnBeforeSelected = { warnBeforeMillis = it },
                onRecurringSelected = { recurringEveryMillis = it },
            )

            if (!state.notificationsEnabled) {
                Warning(
                    message = stringResource(R.string.reminder_permission_notifications),
                    actionText = stringResource(R.string.reminder_action_allow_notifications),
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(POST_NOTIFICATIONS)
                        } else {
                            // Antes do Android 13 nao ha permissao: o utilizador
                            // desligou as notificacoes nas definicoes do sistema.
                            context.openNotificationSettings()
                        }
                    },
                )
            }

            if (!state.canScheduleExact) {
                Warning(
                    message = stringResource(R.string.reminder_exact_alarm_warning),
                    actionText = stringResource(R.string.reminder_action_exact_alarm_settings),
                    onAction = context::openExactAlarmSettings,
                )
            }

            Text(
                text = stringResource(R.string.reminder_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = {
                    val durationMinutes = when {
                        customSelected -> customValue
                        else -> selectedMinutes
                    }
                    onSave(
                        durationMinutes?.let(TimeUnit.MINUTES::toMillis),
                        warnBeforeMillis,
                        recurringEveryMillis,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSave,
            ) {
                Text(stringResource(R.string.reminder_action_save))
            }

            if (reminder != null) {
                TextButton(
                    onClick = onRemove,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.reminder_action_remove))
                }
            }
        }
    }
}

@Composable
private fun Warning(
    message: String,
    actionText: String,
    modifier: Modifier = Modifier,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onAction) { Text(actionText) }
    }
}

@Composable
private fun durationLabel(minutes: Long): String {
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR

    return when {
        minutes < MINUTES_PER_HOUR -> stringResource(R.string.reminder_duration_minutes, minutes.toInt())
        rest == 0L -> stringResource(R.string.reminder_duration_hours, hours.toInt())
        else -> stringResource(
            R.string.reminder_duration_hours_minutes,
            hours.toInt(),
            rest.toInt(),
        )
    }
}

/**
 * Minutos que ainda faltam, arredondados para cima, ou `null` sem prazo.
 *
 * O arredondamento e para cima porque a caixa reabre com o valor que o
 * utilizador vai reconfirmar: mostrar 59 num prazo de 60 min fa-lo-ia perder um
 * minuto a cada alteracao.
 */
private fun ParkingReminder?.remainingMinutes(): Long? {
    val remaining = this?.remainingMillis(System.currentTimeMillis()) ?: return null
    if (remaining <= 0L) return null

    val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining)
    return if (remaining % TimeUnit.MINUTES.toMillis(1) == 0L) minutes else minutes + 1
}

/**
 * O ecra de notificacoes da app so existe no Android 8+; antes disso o mais
 * perto que ha e a pagina da aplicacao, que tambem la leva.
 */
private fun Context.openNotificationSettings() {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    } else {
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:$packageName".toUri(),
        )
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching { startActivity(intent) }
}

/**
 * O ecra de alarmes exatos so existe no Android 12+; antes disso a permissao e
 * concedida na instalacao e nao ha nada para abrir.
 */
private fun Context.openExactAlarmSettings() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

private const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
private const val MINUTES_PER_HOUR = 60L

/** Prazos sugeridos: cobrem o parquimetro curto e a tarde inteira no silo. */
private val DURATION_OPTIONS_MINUTES = listOf(30L, 60L, 120L, 240L)
