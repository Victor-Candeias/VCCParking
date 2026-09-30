package pt.vcc.parking

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import pt.vcc.parking.location.LOCATION_PERMISSIONS
import pt.vcc.parking.location.LocationUiState
import pt.vcc.parking.location.LocationViewModel
import pt.vcc.parking.location.UserLocation
import pt.vcc.parking.location.hasLocationPermission
import pt.vcc.parking.ui.theme.VccParkingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            VccParkingTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LocationRoute(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun LocationRoute(
    modifier: Modifier = Modifier,
    viewModel: LocationViewModel = viewModel(factory = LocationViewModel.Factory),
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        when {
            results.values.any { it } -> viewModel.refreshLocation()
            activity.canAskLocationPermissionAgain() -> viewModel.onPermissionRequired()
            else -> viewModel.onPermissionPermanentlyDenied()
        }
    }

    LaunchedEffect(Unit) {
        if (context.hasLocationPermission()) {
            viewModel.refreshLocation()
        } else {
            viewModel.onPermissionRequired()
        }
    }

    LocationScreen(
        uiState = uiState,
        modifier = modifier,
        onRequestPermission = { permissionLauncher.launch(LOCATION_PERMISSIONS) },
        onRefresh = viewModel::refreshLocation,
        onOpenAppSettings = context::openAppSettings,
        onOpenLocationSettings = context::openLocationSettings,
    )
}

@Composable
private fun LocationScreen(
    uiState: LocationUiState,
    modifier: Modifier = Modifier,
    onRequestPermission: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    onOpenLocationSettings: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(id = R.string.location_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        when (uiState) {
            LocationUiState.Idle -> {
                Message(stringResource(id = R.string.location_idle))
                Button(onClick = onRefresh) {
                    Text(stringResource(id = R.string.location_action_refresh))
                }
            }

            LocationUiState.Loading -> {
                CircularProgressIndicator()
                Message(stringResource(id = R.string.location_loading))
            }

            is LocationUiState.Available -> {
                Text(
                    text = stringResource(
                        id = R.string.location_coordinates,
                        uiState.location.latitude,
                        uiState.location.longitude,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                )
                Message(accuracyText(uiState.location))
                Button(onClick = onRefresh) {
                    Text(stringResource(id = R.string.location_action_refresh))
                }
            }

            LocationUiState.PermissionRequired -> {
                Message(stringResource(id = R.string.location_permission_rationale))
                Button(onClick = onRequestPermission) {
                    Text(stringResource(id = R.string.location_action_allow))
                }
            }

            LocationUiState.PermissionDenied -> {
                Message(stringResource(id = R.string.location_permission_denied))
                Button(onClick = onOpenAppSettings) {
                    Text(stringResource(id = R.string.location_action_settings))
                }
            }

            LocationUiState.LocationDisabled -> {
                Message(stringResource(id = R.string.location_disabled))
                Button(onClick = onOpenLocationSettings) {
                    Text(stringResource(id = R.string.location_action_location_settings))
                }
            }

            LocationUiState.Unavailable -> {
                Message(stringResource(id = R.string.location_unavailable))
                Button(onClick = onRefresh) {
                    Text(stringResource(id = R.string.location_action_retry))
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun accuracyText(location: UserLocation): String {
    val accuracy = location.accuracyMeters
    return if (accuracy == null) {
        stringResource(id = R.string.location_accuracy_unknown)
    } else {
        stringResource(id = R.string.location_accuracy, accuracy.roundToInt())
    }
}

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

@Preview(showBackground = true)
@Composable
private fun LocationScreenAvailablePreview() {
    VccParkingTheme {
        LocationScreen(
            uiState = LocationUiState.Available(
                UserLocation(
                    latitude = 38.7253,
                    longitude = -9.1500,
                    accuracyMeters = 18f,
                    timestampMillis = 0L,
                ),
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LocationScreenPermissionPreview() {
    VccParkingTheme {
        LocationScreen(uiState = LocationUiState.PermissionRequired)
    }
}
