package pt.vcc.parking.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.vcc.parking.location.LOCATION_PERMISSIONS
import pt.vcc.parking.location.LocationUiState
import pt.vcc.parking.location.LocationViewModel
import pt.vcc.parking.location.hasLocationPermission
import pt.vcc.parking.parked.ParkedCarViewModel
import pt.vcc.parking.reminder.ReminderViewModel
import pt.vcc.parking.ui.history.HistoryRoute
import pt.vcc.parking.ui.returnroute.ReturnRouteScreen

/**
 * Liga a localizacao de `vp-02-location` a pesquisa de parques.
 *
 * Os dois ViewModel ficam separados: a localizacao tem estados e permissoes
 * proprios, que nao pertencem a pesquisa, e nenhum deles precisa de conhecer o
 * outro — e esta rota que faz a ponte.
 */
@Composable
fun ParkingRoute(
    modifier: Modifier = Modifier,
    openReturn: Boolean = false,
    onReturnOpened: () -> Unit = {},
    locationViewModel: LocationViewModel = viewModel(factory = LocationViewModel.Factory),
    parkingViewModel: ParkingViewModel = viewModel(factory = ParkingViewModel.Factory),
    parkedCarViewModel: ParkedCarViewModel = viewModel(factory = ParkedCarViewModel.Factory),
    reminderViewModel: ReminderViewModel = viewModel(factory = ReminderViewModel.Factory),
) {
    val context = LocalContext.current
    val activity = LocalActivity.current

    val locationState by locationViewModel.uiState.collectAsStateWithLifecycle()
    val parkingState by parkingViewModel.uiState.collectAsStateWithLifecycle()
    val radiusMeters by parkingViewModel.radiusMeters.collectAsStateWithLifecycle()
    val parkedCarState by parkedCarViewModel.uiState.collectAsStateWithLifecycle()
    val reminderState by reminderViewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        when {
            results.values.any { it } -> locationViewModel.refreshLocation()
            activity.canAskLocationPermissionAgain() -> locationViewModel.onPermissionRequired()
            else -> locationViewModel.onPermissionPermanentlyDenied()
        }
    }

    // A app tem tres ecras e nenhuma biblioteca de navegacao: um unico sinal
    // chega e sobrevive a rotacao. Trazer uma dependencia de navegacao para
    // isto custaria mais do que resolve.
    var screen by rememberSaveable { mutableStateOf(ParkingScreenRoute.Map) }

    LaunchedEffect(Unit) {
        if (context.hasLocationPermission()) {
            locationViewModel.refreshLocation()
        } else {
            locationViewModel.onPermissionRequired()
        }
    }

    LaunchedEffect(locationState) {
        val available = locationState as? LocationUiState.Available ?: return@LaunchedEffect
        parkingViewModel.onUserLocation(available.location)
    }

    // Toque na notificacao persistente de `vp-11-reminders`: a Activity e
    // `singleTop`, por isso o pedido chega como estado e nao como novo arranque.
    LaunchedEffect(openReturn) {
        if (openReturn) {
            screen = ParkingScreenRoute.Return
            onReturnOpened()
        }
    }

    when (screen) {
        ParkingScreenRoute.Return -> {
            BackHandler { screen = ParkingScreenRoute.Map }

            ReturnRouteScreen(
                modifier = modifier,
                onBack = { screen = ParkingScreenRoute.Map },
            )
            return
        }

        ParkingScreenRoute.History -> {
            BackHandler { screen = ParkingScreenRoute.Map }

            HistoryRoute(
                modifier = modifier,
                onBack = { screen = ParkingScreenRoute.Map },
            )
            return
        }

        ParkingScreenRoute.Map -> Unit
    }

    ParkingScreen(
        locationState = locationState,
        parkingState = parkingState,
        radiusMeters = radiusMeters,
        modifier = modifier,
        parkedCarState = parkedCarState,
        reminderState = reminderState,
        onRequestPermission = { permissionLauncher.launch(LOCATION_PERMISSIONS) },
        onRefreshLocation = locationViewModel::refreshLocation,
        onOpenAppSettings = context::openAppSettings,
        onOpenLocationSettings = context::openLocationSettings,
        onRadiusSelected = parkingViewModel::onRadiusSelected,
        onSearchArea = parkingViewModel::searchArea,
        onRetry = parkingViewModel::retry,
        onParkHere = parkedCarViewModel::parkHere,
        onParkAt = { latitude, longitude -> parkedCarViewModel.parkAt(latitude, longitude) },
        onReturnToCar = { screen = ParkingScreenRoute.Return },
        onOpenHistory = { screen = ParkingScreenRoute.History },
        onMoveParkedCar = parkedCarViewModel::moveTo,
        onUpdateParkedCarDetails = parkedCarViewModel::updateDetails,
        onEndParkedCar = parkedCarViewModel::endActive,
        onDismissParkedCarError = parkedCarViewModel::dismissCaptureError,
        onSaveReminder = reminderViewModel::setReminder,
        onExtendReminder = reminderViewModel::extend,
        onRemoveReminder = reminderViewModel::clear,
        onReminderCapabilitiesChanged = reminderViewModel::refreshCapabilities,
    )
}

/** Os tres destinos da app; guardado em `rememberSaveable`, sobrevive a rotacao. */
private enum class ParkingScreenRoute {
    Map,
    Return,
    History,
}

/**
 * `shouldShowRequestPermissionRationale` devolve `false` tanto antes do primeiro
 * pedido como depois de uma recusa definitiva; aqui so e consultado apos uma
 * recusa, pelo que distingue os dois casos.
 */
private fun Activity?.canAskLocationPermissionAgain(): Boolean {
    val activity = this ?: return false
    return LOCATION_PERMISSIONS.any { permission ->
        ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }
}

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

private fun Context.openLocationSettings() {
    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}
