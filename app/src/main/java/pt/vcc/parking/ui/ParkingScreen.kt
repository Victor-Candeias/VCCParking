package pt.vcc.parking.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import pt.vcc.parking.R
import pt.vcc.parking.domain.model.Parking
import pt.vcc.parking.location.LocationUiState
import pt.vcc.parking.location.UserLocation
import pt.vcc.parking.ui.details.ParkingDetailsSheet
import pt.vcc.parking.ui.details.openExternalNavigation
import pt.vcc.parking.ui.list.ParkingList
import pt.vcc.parking.ui.map.ParkingMap

/** Ecra principal da seccao 22 do documento do MVP. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkingScreen(
    locationState: LocationUiState,
    parkingState: ParkingUiState,
    radiusMeters: Int,
    modifier: Modifier = Modifier,
    onRequestPermission: () -> Unit = {},
    onRefreshLocation: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    onOpenLocationSettings: () -> Unit = {},
    onRadiusSelected: (Int) -> Unit = {},
    onSearchArea: (latitude: Double, longitude: Double) -> Unit = { _, _ -> },
    onRetry: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val navigationUnavailable = stringResource(R.string.parking_navigation_unavailable)

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val content = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        if (locationState is LocationUiState.Available) {
            ParkingContent(
                userLocation = locationState.location,
                parkingState = parkingState,
                radiusMeters = radiusMeters,
                modifier = content,
                onRadiusSelected = onRadiusSelected,
                onSearchArea = onSearchArea,
                onRetry = onRetry,
                onNavigate = { parking ->
                    if (!context.openExternalNavigation(parking)) {
                        scope.launch { snackbarHostState.showSnackbar(navigationUnavailable) }
                    }
                },
            )
        } else {
            LocationStatus(
                uiState = locationState,
                modifier = content,
                onRequestPermission = onRequestPermission,
                onRefresh = onRefreshLocation,
                onOpenAppSettings = onOpenAppSettings,
                onOpenLocationSettings = onOpenLocationSettings,
            )
        }
    }
}

@Composable
private fun ParkingContent(
    userLocation: UserLocation,
    parkingState: ParkingUiState,
    radiusMeters: Int,
    modifier: Modifier = Modifier,
    onRadiusSelected: (Int) -> Unit = {},
    onSearchArea: (latitude: Double, longitude: Double) -> Unit = { _, _ -> },
    onRetry: () -> Unit = {},
    onNavigate: (Parking) -> Unit = {},
) {
    val parking = (parkingState as? ParkingUiState.Success)?.parking.orEmpty()

    // Guardar o id e nao o parque evita manter uma copia desatualizada da lista
    // depois de uma nova pesquisa.
    var selectedParkingId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedParking = parking.firstOrNull { it.id == selectedParkingId }

    var mapCenter by remember {
        mutableStateOf(MapCenter(userLocation.latitude, userLocation.longitude))
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(MAP_WEIGHT),
        ) {
            ParkingMap(
                userLocation = userLocation,
                parking = parking,
                modifier = Modifier.fillMaxSize(),
                onParkingSelected = { selectedParkingId = it.id },
                onCenterChanged = { latitude, longitude ->
                    mapCenter = MapCenter(latitude, longitude)
                },
            )

            FilledTonalButton(
                onClick = { onSearchArea(mapCenter.latitude, mapCenter.longitude) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp),
            ) {
                Text(stringResource(R.string.parking_action_search_area))
            }

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

        ResultsHeader(
            parkingState = parkingState,
            radiusMeters = radiusMeters,
            onRadiusSelected = onRadiusSelected,
            onRetry = onRetry,
        )

        HorizontalDivider()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(LIST_WEIGHT),
        ) {
            ParkingResults(
                parkingState = parkingState,
                onRetry = onRetry,
                onParkingSelected = { selectedParkingId = it.id },
            )
        }
    }

    if (selectedParking != null) {
        ParkingDetailsSheet(
            parking = selectedParking,
            onDismiss = { selectedParkingId = null },
            onNavigate = onNavigate,
        )
    }
}

/** Contagem de resultados e seletor de raio, como no esboco da seccao 22. */
@Composable
private fun ResultsHeader(
    parkingState: ParkingUiState,
    radiusMeters: Int,
    modifier: Modifier = Modifier,
    onRadiusSelected: (Int) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (parkingState) {
            ParkingUiState.Idle -> Text(
                text = stringResource(R.string.parking_idle),
                style = MaterialTheme.typography.bodyMedium,
            )

            ParkingUiState.Loading -> Text(
                text = stringResource(R.string.parking_loading),
                style = MaterialTheme.typography.bodyMedium,
            )

            ParkingUiState.Error -> Text(
                text = stringResource(R.string.parking_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )

            is ParkingUiState.Success -> {
                Text(
                    text = pluralStringResource(
                        R.plurals.parking_found,
                        parkingState.parking.size,
                        parkingState.parking.size,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                CacheNotice(state = parkingState, onRetry = onRetry)
            }
        }

        RadiusSelector(
            radiusMeters = radiusMeters,
            onRadiusSelected = onRadiusSelected,
        )
    }
}

/** Mensagens da seccao 27: a de falha so aparece quando a cache ja esta velha. */
@Composable
private fun CacheNotice(
    state: ParkingUiState.Success,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    if (!state.fromCache) return

    val now = remember(state) { System.currentTimeMillis() }
    val stale = remember(state, now) { state.isStale(now) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (stale) {
                stringResource(R.string.parking_error_cache)
            } else {
                cacheAgeLabel(now - state.updatedAtMillis)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )

        if (stale) {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.parking_action_retry))
            }
        }
    }
}

@Composable
private fun cacheAgeLabel(ageMillis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ageMillis).coerceAtLeast(0L)
    return if (minutes < 1) {
        stringResource(R.string.parking_cache_notice_recent)
    } else {
        stringResource(R.string.parking_cache_notice, minutes.toInt())
    }
}

/** Raios da seccao 20. */
@Composable
private fun RadiusSelector(
    radiusMeters: Int,
    modifier: Modifier = Modifier,
    onRadiusSelected: (Int) -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SearchRadius.OPTIONS_METERS.forEach { option ->
            FilterChip(
                selected = option == radiusMeters,
                onClick = { onRadiusSelected(option) },
                label = { Text(radiusLabel(option)) },
            )
        }
    }
}

@Composable
private fun ParkingResults(
    parkingState: ParkingUiState,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onParkingSelected: (Parking) -> Unit = {},
) {
    when (parkingState) {
        ParkingUiState.Idle -> Unit

        ParkingUiState.Loading -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        ParkingUiState.Error -> CenteredMessage(
            message = stringResource(R.string.parking_error),
            modifier = modifier,
            actionText = stringResource(R.string.parking_action_retry),
            onAction = onRetry,
        )

        is ParkingUiState.Success -> if (parkingState.parking.isEmpty()) {
            CenteredMessage(
                message = stringResource(R.string.parking_empty),
                modifier = modifier,
            )
        } else {
            ParkingList(
                parking = parkingState.parking,
                modifier = modifier.fillMaxSize(),
                onParkingSelected = onParkingSelected,
            )
        }
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
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (actionText != null) {
            Button(onClick = onAction) {
                Text(actionText)
            }
        }
    }
}

private data class MapCenter(val latitude: Double, val longitude: Double)

/** O mapa fica com pouco menos de metade do ecra para a lista continuar util. */
private const val MAP_WEIGHT = 0.45f
private const val LIST_WEIGHT = 0.55f
