package pt.vcc.parking.ui.parked

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.vcc.parking.R
import pt.vcc.parking.domain.model.ParkedCar
import pt.vcc.parking.domain.model.ParkingReminder

/**
 * Resumo do carro estacionado para o painel recolhido do ecra principal
 * (`vp-18-map-first-ui`).
 *
 * O cartao completo ocupava quase meio ecra e nao deixava espaco para a lista
 * de parques; aqui fica so o essencial e a accao principal. O resto abre em
 * [ParkedCarDetailsSheet].
 */
@Composable
fun ParkedCarSummary(
    parkedCar: ParkedCar,
    modifier: Modifier = Modifier,
    reminder: ParkingReminder? = null,
    onReturnToCar: () -> Unit = {},
    onOpenDetails: () -> Unit = {},
) {
    val nowMillis = rememberNowMillis()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = stringResource(R.string.parked_action_details),
                onClick = onOpenDetails,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_map_car),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.parked_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = parkedSinceLabel(parkedCar.parkedAtMillis, nowMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Sem prazo nao ha nada urgente a dizer; com prazo, o tempo que
                // falta (ou o aviso de expirado) tem de estar sempre a vista.
                if (reminder?.hasDeadline == true) {
                    ReminderStatus(
                        reminder = reminder,
                        nowMillis = nowMillis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Button(onClick = onReturnToCar) {
                Text(stringResource(R.string.return_action_guide_me))
            }
        }
    }
}

/** Cartao completo do carro, com todas as accoes de `vp-08`/`vp-11`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkedCarDetailsSheet(
    parkedCar: ParkedCar,
    modifier: Modifier = Modifier,
    reminder: ParkingReminder? = null,
    onDismiss: () -> Unit = {},
    onReturnToCar: () -> Unit = {},
    onEdit: () -> Unit = {},
    onAdjustOnMap: () -> Unit = {},
    onEnd: () -> Unit = {},
    onSetReminder: () -> Unit = {},
    onExtendReminder: () -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            ParkedCarCard(
                parkedCar = parkedCar,
                reminder = reminder,
                onReturnToCar = onReturnToCar,
                onEdit = onEdit,
                onAdjustOnMap = onAdjustOnMap,
                onEnd = onEnd,
                onSetReminder = onSetReminder,
                onExtendReminder = onExtendReminder,
            )
        }
    }
}
