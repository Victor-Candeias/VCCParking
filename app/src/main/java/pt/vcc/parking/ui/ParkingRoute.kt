package pt.vcc.parking.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
    locationViewModel: LocationViewModel = viewModel(factory = LocationViewModel.Factory),
    parkingViewModel: ParkingViewModel = viewModel(factory = ParkingViewModel.Factory),
    parkedCarViewModel: ParkedCarViewModel = viewModel(factory = ParkedCarViewModel.Factory),
) {
    val context = LocalContext.current
    val activity = LocalActivity.current

    val locationState by locationViewModel.uiState.collectAsStateWithLifecycle()
    val parkingState by parkingViewModel.uiState.collectAsStateWithLifecycle()
    val radiusMeters by parkingViewModel.radiusMeters.collectAsStateWithLifecycle()
    val parkedCarState by parkedCarViewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        when {
            results.values.any { it } -> locationViewModel.refreshLocation()
            activity.canAskLocationPermissionAgain() -> locationViewModel.onPermissionRequired()
            else -> locationViewModel.onPermissionPermanentlyDenied()
        }
    }

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

    ParkingScreen(
        locationState = locationState,
        parkingState = parkingState,
        radiusMeters = radiusMeters,
        modifier = modifier,
        parkedCarState = parkedCarState,
        onRequestPermission = { permissionLauncher.launch(LOCATION_PERMISSIONS) },
        onRefreshLocation = locationViewModel::refreshLocation,
        onOpenAppSettings = context::openAppSettings,
        onOpenLocationSettings = context::openLocationSettings,
        onRadiusSelected = parkingViewModel::onRadiusSelected,
        onSearchArea = parkingViewModel::searchArea,
        onRetry = parkingViewModel::retry,
        onParkHere = parkedCarViewModel::parkHere,
        onParkAt = { latitude, longitude -> parkedCarViewModel.parkAt(latitude, longitude) },
        onMoveParkedCar = parkedCarViewModel::moveTo,
        onUpdateParkedCarDetails = parkedCarViewModel::updateDetails,
        onEndParkedCar = parkedCarViewModel::endActive,
        onDismissParkedCarError = parkedCarViewModel::dismissCaptureError,
    )
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
