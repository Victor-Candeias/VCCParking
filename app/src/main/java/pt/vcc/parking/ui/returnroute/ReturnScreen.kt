package pt.vcc.parking.ui.returnroute

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import pt.vcc.parking.R
import pt.vcc.parking.domain.model.ParkedCar
import pt.vcc.parking.returnroute.ReturnUiState
import pt.vcc.parking.returnroute.ReturnViewModel
import pt.vcc.parking.ui.details.openWalkingNavigation
import pt.vcc.parking.ui.map.MapPoint
import pt.vcc.parking.ui.map.ParkingMap
import pt.vcc.parking.ui.parked.ParkedCarPhoto

/** Liga o [ReturnViewModel] ao ecra, como `ParkingRoute` faz para a pesquisa. */
@Composable
fun ReturnRouteScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: ReturnViewModel = viewModel(factory = ReturnViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ReturnScreen(
        uiState = uiState,
        modifier = modifier,
        onBack = onBack,
        onEndParking = {
            viewModel.endParking()
            onBack()
        },
    )
}

/**
 * Ecra de regresso ao carro (`vp-09-return-route`).
 *
 * A nota e a fotografia de `vp-08-park-save` ficam sempre visiveis: num parque
 * grande, «piso -2, lugar 134» resolve o problema que nenhuma rota resolve,
 * porque a ultima dezena de metros e vertical e nao esta mapeada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReturnScreen(
    uiState: ReturnUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onEndParking: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val navigationUnavailable = stringResource(R.string.parking_navigation_unavailable)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.return_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.return_action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val content = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when (uiState) {
            ReturnUiState.Locating -> CenteredProgress(
                message = stringResource(R.string.return_locating),
                modifier = content,
            )

            ReturnUiState.NoParkedCar -> CenteredMessage(
                message = stringResource(R.string.return_no_parked_car),
                modifier = content,
                actionText = stringResource(R.string.return_action_save_position),
                onAction = onBack,
            )

            is ReturnUiState.Guiding -> Guidance(
                state = uiState,
                modifier = content,
                onOpenExternalNavigation = {
                    val opened = context.openWalkingNavigation(
                        latitude = uiState.parkedCar.latitude,
                        longitude = uiState.parkedCar.longitude,
                        label = context.getString(R.string.parked_marker),
                    )
                    if (!opened) {
                        scope.launch { snackbarHostState.showSnackbar(navigationUnavailable) }
                    }
                },
                onEndParking = onEndParking,
            )
        }
    }
}

@Composable
private fun Guidance(
    state: ReturnUiState.Guiding,
    modifier: Modifier = Modifier,
    onOpenExternalNavigation: () -> Unit = {},
    onEndParking: () -> Unit = {},
) {
    var confirmingEnd by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(MAP_WEIGHT),
        ) {
            ParkingMap(
                userLocation = state.userLocation,
                parking = emptyList(),
                modifier = Modifier.fillMaxSize(),
                parkedCar = state.parkedCar,
                routePoints = state.route?.points.orEmpty()
                    .map { MapPoint(it.latitude, it.longitude) },
            )

            // A politica de tiles do OSM exige atribuicao visivel.
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            ) {
                Text(
                    text = stringResource(R.string.osm_attribution),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(DETAILS_WEIGHT)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReturnCompass(
                distanceMeters = state.distanceMeters,
                relativeBearingDegrees = state.relativeBearingDegrees,
                arrived = state.arrived,
            )

            if (state.headingDegrees == null && !state.arrived) {
                Hint(stringResource(R.string.return_no_compass))
            }

            state.route?.let { route ->
                Hint(
                    stringResource(
                        R.string.return_route_summary,
                        route.distanceMeters.roundToInt(),
                        (route.durationSeconds / SECONDS_PER_MINUTE).roundToInt()
                            .coerceAtLeast(1),
                    ),
                )
            }

            // Discreto por decisao da fase 2: o modo bussola continua a servir,
            // e um alarme sobre routing so assustaria sem dar alternativa.
            if (state.routeUnavailable) {
                Hint(stringResource(R.string.return_route_unavailable))
            }

            ParkedCarReminder(parkedCar = state.parkedCar)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenExternalNavigation,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.return_action_open_external))
                }
                OutlinedButton(
                    onClick = { confirmingEnd = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.parked_action_end))
                }
            }
        }
    }

    if (confirmingEnd) {
        AlertDialog(
            onDismissRequest = { confirmingEnd = false },
            title = { Text(stringResource(R.string.parked_end_confirm_title)) },
            text = { Text(stringResource(R.string.parked_end_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingEnd = false
                        onEndParking()
                    },
                ) {
                    Text(stringResource(R.string.parked_end_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingEnd = false }) {
                    Text(stringResource(R.string.parked_action_cancel))
                }
            },
        )
    }
}

/** A nota e a fotografia valem mais do que a rota nos ultimos metros. */
@Composable
private fun ParkedCarReminder(parkedCar: ParkedCar, modifier: Modifier = Modifier) {
    val note = parkedCar.note
    val photoUri = parkedCar.photoUri
    if (note == null && photoUri == null) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        note?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        photoUri?.let {
            ParkedCarPhoto(
                photoUri = it,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PHOTO_HEIGHT)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}

@Composable
private fun Hint(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun CenteredProgress(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(text = message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CenteredMessage(
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (actionText != null) {
            OutlinedButton(onClick = onAction) {
                Text(actionText)
            }
        }
    }
}

private const val SECONDS_PER_MINUTE = 60.0

/** O mapa fica maior do que na pesquisa: aqui nao ha lista a disputar o espaco. */
private const val MAP_WEIGHT = 0.5f
private const val DETAILS_WEIGHT = 0.5f
private val PHOTO_HEIGHT = 160.dp
