package pt.vcc.parking.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import pt.vcc.parking.R
import pt.vcc.parking.domain.ParkingFilter
import pt.vcc.parking.domain.model.ParkedCar
import pt.vcc.parking.domain.model.Parking
import pt.vcc.parking.location.LocationUiState
import pt.vcc.parking.location.UserLocation
import pt.vcc.parking.parked.ParkedCarCaptureError
import pt.vcc.parking.parked.ParkedCarUiState
import pt.vcc.parking.reminder.ReminderUiState
import pt.vcc.parking.ui.details.ParkingDetailsSheet
import pt.vcc.parking.ui.details.openExternalNavigation
import pt.vcc.parking.ui.filters.ParkingFilterBar
import pt.vcc.parking.ui.filters.ParkingFilterSheet
import pt.vcc.parking.ui.list.ParkingList
import pt.vcc.parking.ui.map.ParkingMap
import pt.vcc.parking.ui.map.USER_ZOOM
import pt.vcc.parking.ui.parked.ParkHereButton
import pt.vcc.parking.ui.parked.ParkedCarDetailsSheet
import pt.vcc.parking.ui.parked.ParkedCarEditSheet
import pt.vcc.parking.ui.parked.ParkedCarPinPicker
import pt.vcc.parking.ui.parked.ParkedCarSummary
import pt.vcc.parking.ui.reminder.ReminderSheet

/** Ecra principal da seccao 22 do documento do MVP. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkingScreen(
    locationState: LocationUiState,
    parkingState: ParkingUiState,
    radiusMeters: Int,
    modifier: Modifier = Modifier,
    parkedCarState: ParkedCarUiState = ParkedCarUiState.Empty,
    reminderState: ReminderUiState = ReminderUiState(),
    filter: ParkingFilter = ParkingFilter(),
    onRequestPermission: () -> Unit = {},
    onRefreshLocation: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    onOpenLocationSettings: () -> Unit = {},
    onRadiusSelected: (Int) -> Unit = {},
    onSearchArea: (latitude: Double, longitude: Double) -> Unit = { _, _ -> },
    onRetry: () -> Unit = {},
    onParkHere: (Parking?) -> Unit = {},
    onParkAt: (latitude: Double, longitude: Double) -> Unit = { _, _ -> },
    onReturnToCar: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onMoveParkedCar: (id: Long, latitude: Double, longitude: Double) -> Unit = { _, _, _ -> },
    onUpdateParkedCarDetails: (id: Long, note: String?, photoUri: String?) -> Unit =
        { _, _, _ -> },
    onEndParkedCar: () -> Unit = {},
    onDismissParkedCarError: () -> Unit = {},
    onFilterChanged: (ParkingFilter) -> Unit = {},
    onSaveReminder: (
        durationMillis: Long?,
        warnBeforeMillis: Long,
        recurringEveryMillis: Long?,
    ) -> Unit = { _, _, _ -> },
    onExtendReminder: () -> Unit = {},
    onRemoveReminder: () -> Unit = {},
    onReminderCapabilitiesChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val navigationUnavailable = stringResource(R.string.parking_navigation_unavailable)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                // O historico de `vp-10-history` nao tem ecra proprio na
                // navegacao: e um destino secundario a partir do mapa.
                actions = {
                    TextButton(onClick = onOpenHistory) {
                        Text(stringResource(R.string.history_action_open))
                    }
                },
            )
        },
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
                parkedCarState = parkedCarState,
                reminderState = reminderState,
                filter = filter,
                onRadiusSelected = onRadiusSelected,
                onSearchArea = onSearchArea,
                onRetry = onRetry,
                onNavigate = { parking ->
                    if (!context.openExternalNavigation(parking)) {
                        scope.launch { snackbarHostState.showSnackbar(navigationUnavailable) }
                    }
                },
                onParkHere = onParkHere,
                onParkAt = onParkAt,
                onReturnToCar = onReturnToCar,
                onMoveParkedCar = onMoveParkedCar,
                onUpdateParkedCarDetails = onUpdateParkedCarDetails,
                onEndParkedCar = onEndParkedCar,
                onDismissParkedCarError = onDismissParkedCarError,
                onRequestPermission = onRequestPermission,
                onFilterChanged = onFilterChanged,
                onSaveReminder = onSaveReminder,
                onExtendReminder = onExtendReminder,
                onRemoveReminder = onRemoveReminder,
                onReminderCapabilitiesChanged = onReminderCapabilitiesChanged,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParkingContent(
    userLocation: UserLocation,
    parkingState: ParkingUiState,
    radiusMeters: Int,
    modifier: Modifier = Modifier,
    parkedCarState: ParkedCarUiState = ParkedCarUiState.Empty,
    reminderState: ReminderUiState = ReminderUiState(),
    filter: ParkingFilter = ParkingFilter(),
    onRadiusSelected: (Int) -> Unit = {},
    onSearchArea: (latitude: Double, longitude: Double) -> Unit = { _, _ -> },
    onRetry: () -> Unit = {},
    onNavigate: (Parking) -> Unit = {},
    onParkHere: (Parking?) -> Unit = {},
    onParkAt: (latitude: Double, longitude: Double) -> Unit = { _, _ -> },
    onReturnToCar: () -> Unit = {},
    onMoveParkedCar: (id: Long, latitude: Double, longitude: Double) -> Unit = { _, _, _ -> },
    onUpdateParkedCarDetails: (id: Long, note: String?, photoUri: String?) -> Unit =
        { _, _, _ -> },
    onEndParkedCar: () -> Unit = {},
    onDismissParkedCarError: () -> Unit = {},
    onRequestPermission: () -> Unit = {},
    onFilterChanged: (ParkingFilter) -> Unit = {},
    onSaveReminder: (
        durationMillis: Long?,
        warnBeforeMillis: Long,
        recurringEveryMillis: Long?,
    ) -> Unit = { _, _, _ -> },
    onExtendReminder: () -> Unit = {},
    onRemoveReminder: () -> Unit = {},
    onReminderCapabilitiesChanged: () -> Unit = {},
) {
    val success = parkingState as? ParkingUiState.Success
    val parking = success?.parking.orEmpty()
    val parkedCar = parkedCarState.parkedCar

    // O mapa mostra o mesmo que a lista: os que cumprem o filtro e os incertos.
    // Deixar no mapa um parque que a lista escondeu seria dar duas respostas
    // diferentes a mesma pergunta.
    val visibleParking = remember(success) {
        success?.filtered?.let { it.matching + it.unknown }.orEmpty()
    }

    // Guardar o id e nao o parque evita manter uma copia desatualizada da lista
    // depois de uma nova pesquisa.
    var selectedParkingId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedParking = parking.firstOrNull { it.id == selectedParkingId }

    var editingParkedCar by rememberSaveable { mutableStateOf(false) }
    var showingParkedCarDetails by rememberSaveable { mutableStateOf(false) }
    var editingReminder by rememberSaveable { mutableStateOf(false) }
    var editingFilters by rememberSaveable { mutableStateOf(false) }
    var pinTarget by remember { mutableStateOf<PinTarget?>(null) }

    var mapCenter by remember {
        mutableStateOf(MapCenter(userLocation.latitude, userLocation.longitude))
    }

    // Nao e saveable de proposito: apos uma rotacao o pedido anterior ja foi
    // cumprido e repeti-lo anularia o gesto que o utilizador fez entretanto.
    var recenterRequest by remember { mutableIntStateOf(0) }

    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        // A lista nunca desaparece de todo: recolhida continua a mostrar o
        // resumo e o carro estacionado.
        skipHiddenState = true,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    val density = LocalDensity.current
    var dragHandleHeightPx by remember { mutableIntStateOf(0) }
    var summaryHeightPx by remember { mutableIntStateOf(0) }
    val peekHeight = if (summaryHeightPx == 0) {
        SHEET_PEEK_FALLBACK
    } else {
        with(density) { (dragHandleHeightPx + summaryHeightPx).toDp() }
    }

    // O painel recolhido continua desenhado abaixo da area visivel; sem recorte
    // aparecia por tras da barra de navegacao do sistema.
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        // Aberto, o painel fica com metade do ecra e o mapa com a outra metade.
        val expandedSheetHeight = maxHeight * SHEET_EXPANDED_FRACTION

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = peekHeight,
            sheetDragHandle = {
                Box(modifier = Modifier.onSizeChanged { dragHandleHeightPx = it.height }) {
                    BottomSheetDefaults.DragHandle()
                }
            },
            sheetContent = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            (expandedSheetHeight - with(density) { dragHandleHeightPx.toDp() })
                                .coerceAtLeast(0.dp),
                        ),
                ) {
                    // So este bloco cabe no painel recolhido: o que importa sem
                    // abrir a lista e o carro e quantos parques ha por perto.
                    Column(modifier = Modifier.onSizeChanged { summaryHeightPx = it.height }) {
                        if (parkedCar != null) {
                            ParkedCarSummary(
                                parkedCar = parkedCar,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                                reminder = reminderState.reminder,
                                onReturnToCar = onReturnToCar,
                                onOpenDetails = { showingParkedCarDetails = true },
                            )
                        }

                        ResultsSummary(
                            parkingState = parkingState,
                            onRetry = onRetry,
                        )
                    }

                    SearchControls(
                        radiusMeters = radiusMeters,
                        filter = filter,
                        onRadiusSelected = onRadiusSelected,
                        onFilterChanged = onFilterChanged,
                        onOpenFilters = { editingFilters = true },
                    )

                    HorizontalDivider()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        ParkingResults(
                            parkingState = parkingState,
                            onRetry = onRetry,
                            onParkingSelected = { selectedParkingId = it.id },
                        )
                    }
                }
            },
        ) {
            // O mapa acompanha o topo do painel em vez de ficar por baixo dele:
            // assim o centro (a posicao do utilizador) continua visivel com o
            // painel aberto. A altura e lida na fase de layout para o arrasto nao
            // recompor o ecra a cada frame.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val sheetTop = runCatching { sheetState.requireOffset() }.getOrNull()
                        val height = sheetTop?.roundToInt()
                            ?.coerceIn(0, constraints.maxHeight)
                            ?: constraints.maxHeight
                        val placeable = measurable.measure(
                            constraints.copy(minHeight = height, maxHeight = height),
                        )
                        layout(placeable.width, height) { placeable.place(0, 0) }
                    }
                    .clipToBounds(),
            ) {
                ParkingMap(
                    userLocation = userLocation,
                    parking = visibleParking,
                    modifier = Modifier.fillMaxSize(),
                    parkedCar = parkedCar,
                    zoom = USER_ZOOM,
                    recenterRequest = recenterRequest,
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

                // So aparece sem carro guardado: com um registo ativo, a accao do
                // cartao e terminar, nao voltar a guardar por cima.
                if (parkedCar == null) {
                    ParkHereButton(
                        capturing = parkedCarState is ParkedCarUiState.Capturing,
                        onClick = { onParkHere(null) },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp),
                    )
                }

                Column(
                    modifier = Modifier.align(Alignment.BottomEnd),
                    horizontalAlignment = Alignment.End,
                ) {
                    SmallFloatingActionButton(
                        onClick = { recenterRequest++ },
                        modifier = Modifier.padding(12.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_my_location),
                            contentDescription = stringResource(R.string.parking_action_my_location),
                        )
                    }

                    // A politica de tiles do OSM exige atribuicao visivel.
                    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)) {
                        Text(
                            text = stringResource(R.string.osm_attribution),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }

    if (editingFilters) {
        ParkingFilterSheet(
            filter = filter,
            onFilterChanged = onFilterChanged,
            onDismiss = { editingFilters = false },
        )
    }

    if (selectedParking != null) {
        ParkingDetailsSheet(
            parking = selectedParking,
            onDismiss = { selectedParkingId = null },
            onNavigate = onNavigate,
            onParkHere = { chosen ->
                selectedParkingId = null
                onParkHere(chosen)
            },
        )
    }

    if (showingParkedCarDetails && parkedCar != null) {
        ParkedCarDetailsSheet(
            parkedCar = parkedCar,
            reminder = reminderState.reminder,
            onDismiss = { showingParkedCarDetails = false },
            onReturnToCar = {
                showingParkedCarDetails = false
                onReturnToCar()
            },
            onEdit = {
                showingParkedCarDetails = false
                editingParkedCar = true
            },
            onAdjustOnMap = {
                showingParkedCarDetails = false
                pinTarget = PinTarget(
                    latitude = parkedCar.latitude,
                    longitude = parkedCar.longitude,
                    parkedCarId = parkedCar.id,
                )
            },
            onEnd = {
                showingParkedCarDetails = false
                onEndParkedCar()
            },
            onSetReminder = {
                showingParkedCarDetails = false
                // As permissoes podem ter mudado nas definicoes do sistema
                // enquanto a app esteve de lado: reler antes de abrir.
                onReminderCapabilitiesChanged()
                editingReminder = true
            },
            onExtendReminder = onExtendReminder,
        )
    }

    if (editingParkedCar && parkedCar != null) {
        ParkedCarEditSheet(
            parkedCar = parkedCar,
            onDismiss = { editingParkedCar = false },
            onSave = { note, photoUri ->
                editingParkedCar = false
                onUpdateParkedCarDetails(parkedCar.id, note, photoUri)
            },
        )
    }

    if (editingReminder && parkedCar != null) {
        ReminderSheet(
            state = reminderState,
            onDismiss = { editingReminder = false },
            onSave = { durationMillis, warnBeforeMillis, recurringEveryMillis ->
                editingReminder = false
                onSaveReminder(durationMillis, warnBeforeMillis, recurringEveryMillis)
            },
            onRemove = {
                editingReminder = false
                onRemoveReminder()
            },
            onCapabilitiesChanged = onReminderCapabilitiesChanged,
        )
    }
    pinTarget?.let { target ->
        ParkedCarPinPicker(
            initialLatitude = target.latitude,
            initialLongitude = target.longitude,
            onDismiss = { pinTarget = null },
            onConfirm = { latitude, longitude ->
                pinTarget = null
                if (target.parkedCarId == null) {
                    onParkAt(latitude, longitude)
                } else {
                    onMoveParkedCar(target.parkedCarId, latitude, longitude)
                }
            },
        )
    }

    if (parkedCarState is ParkedCarUiState.CaptureFailed) {
        CaptureFailedDialog(
            reason = parkedCarState.reason,
            onDismiss = onDismissParkedCarError,
            onRequestPermission = {
                onDismissParkedCarError()
                onRequestPermission()
            },
            onPickOnMap = {
                onDismissParkedCarError()
                pinTarget = PinTarget(mapCenter.latitude, mapCenter.longitude)
            },
        )
    }
}

/**
 * Qualquer falha de captura termina na mesma saida — marcar o ponto a mao — mas
 * so a falta de permissao tem outra coisa a oferecer antes disso.
 */
@Composable
private fun CaptureFailedDialog(
    reason: ParkedCarCaptureError,
    onDismiss: () -> Unit = {},
    onRequestPermission: () -> Unit = {},
    onPickOnMap: () -> Unit = {},
) {
    val message = when (reason) {
        ParkedCarCaptureError.PermissionMissing -> R.string.parked_error_permission
        ParkedCarCaptureError.LocationDisabled -> R.string.parked_error_location_disabled
        ParkedCarCaptureError.Unavailable -> R.string.parked_error_unavailable
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.parked_title)) },
        text = { Text(stringResource(message)) },
        confirmButton = {
            TextButton(onClick = onPickOnMap) {
                Text(stringResource(R.string.parked_action_pick_on_map))
            }
        },
        dismissButton = {
            if (reason == ParkedCarCaptureError.PermissionMissing) {
                TextButton(onClick = onRequestPermission) {
                    Text(stringResource(R.string.location_action_allow))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.parked_action_dismiss))
                }
            }
        },
    )
}

/** O carro ativo esta em dois estados diferentes; esta extensao evita repeti-lo. */
private val ParkedCarUiState.parkedCar: ParkedCar?
    get() = when (this) {
        is ParkedCarUiState.Active -> parkedCar
        is ParkedCarUiState.CaptureFailed -> parkedCar
        else -> null
    }

/** `parkedCarId` a `null` significa um registo novo em vez de uma correcao. */
private data class PinTarget(
    val latitude: Double,
    val longitude: Double,
    val parkedCarId: Long? = null,
)

/** Contagem e estado da pesquisa: e o que fica visivel com o painel recolhido. */
@Composable
private fun ResultsSummary(
    parkingState: ParkingUiState,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
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

            // A accao fica junto da mensagem para estar acessivel mesmo com o
            // painel recolhido, onde a lista (e o seu botao) nao se ve.
            ParkingUiState.Error -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.parking_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.parking_action_retry))
                }
            }

            is ParkingUiState.Success -> {
                // Com filtro ligado a contagem e a do que esta mesmo a ser
                // mostrado, incertos incluidos; a contagem total escondia que
                // ha parques de fora.
                val count = parkingState.filtered.total

                Text(
                    text = pluralStringResource(R.plurals.parking_found, count, count),
                    style = MaterialTheme.typography.titleMedium,
                )
                CacheNotice(state = parkingState, onRetry = onRetry)
            }
        }
    }
}

/** Seletor de raio e filtros, como no esboco da seccao 22. */
@Composable
private fun SearchControls(
    radiusMeters: Int,
    modifier: Modifier = Modifier,
    filter: ParkingFilter = ParkingFilter(),
    onRadiusSelected: (Int) -> Unit = {},
    onFilterChanged: (ParkingFilter) -> Unit = {},
    onOpenFilters: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RadiusSelector(
            radiusMeters = radiusMeters,
            onRadiusSelected = onRadiusSelected,
        )

        ParkingFilterBar(
            filter = filter,
            onFilterChanged = onFilterChanged,
            onOpenFilters = onOpenFilters,
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
                filtered = parkingState.filtered,
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

/** Painel aberto: metade do ecra para a lista, a outra metade para o mapa. */
private const val SHEET_EXPANDED_FRACTION = 0.5f

/** Usado so ate o resumo do painel ser medido pela primeira vez. */
private val SHEET_PEEK_FALLBACK = 96.dp
