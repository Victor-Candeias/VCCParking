package pt.vcc.parking.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

sealed interface LocationResult {
    data class Available(val location: UserLocation) : LocationResult

    data object PermissionMissing : LocationResult

    data object LocationDisabled : LocationResult

    data class Unavailable(val cause: Throwable? = null) : LocationResult
}

interface LocationProvider {
    suspend fun currentLocation(): LocationResult
}

class FusedLocationProvider(context: Context) : LocationProvider {

    private val appContext = context.applicationContext

    private val client: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(appContext)
    }

    override suspend fun currentLocation(): LocationResult {
        if (!appContext.hasLocationPermission()) return LocationResult.PermissionMissing
        if (!isSystemLocationEnabled()) return LocationResult.LocationDisabled

        return try {
            val location = requestCurrentLocation() ?: lastKnownLocation()
            if (location == null) {
                Log.w(TAG, "Sem leitura de localizacao disponivel")
                LocationResult.Unavailable()
            } else {
                LocationResult.Available(location.toUserLocation())
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (security: SecurityException) {
            LocationResult.PermissionMissing
        } catch (error: Exception) {
            Log.w(TAG, "Falha ao obter localizacao: ${error.javaClass.simpleName}")
            LocationResult.Unavailable(error)
        }
    }

    private fun isSystemLocationEnabled(): Boolean {
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentLocation(): Location? = suspendCancellableCoroutine { continuation ->
        val cancellationSource = CancellationTokenSource()
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            .setMaxUpdateAgeMillis(MAX_LOCATION_AGE_MILLIS)
            .setDurationMillis(LOCATION_TIMEOUT_MILLIS)
            .build()

        continuation.invokeOnCancellation { cancellationSource.cancel() }

        client.getCurrentLocation(request, cancellationSource.token)
            .addOnSuccessListener { location ->
                if (continuation.isActive) continuation.resume(location)
            }
            .addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            .addOnCanceledListener {
                if (continuation.isActive) continuation.resume(null)
            }
    }

    @SuppressLint("MissingPermission")
    private suspend fun lastKnownLocation(): Location? = suspendCancellableCoroutine { continuation ->
        client.lastLocation
            .addOnSuccessListener { location ->
                if (continuation.isActive) continuation.resume(location)
            }
            .addOnFailureListener {
                if (continuation.isActive) continuation.resume(null)
            }
            .addOnCanceledListener {
                if (continuation.isActive) continuation.resume(null)
            }
    }

    private companion object {
        const val TAG = "LocationProvider"
        const val MAX_LOCATION_AGE_MILLIS = 60_000L
        const val LOCATION_TIMEOUT_MILLIS = 20_000L
    }
}

private fun Location.toUserLocation(): UserLocation = UserLocation(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = if (hasAccuracy()) accuracy else null,
    timestampMillis = time,
)
