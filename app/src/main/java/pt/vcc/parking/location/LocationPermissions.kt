package pt.vcc.parking.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Basta uma das permissões ser concedida: a localização aproximada é suficiente para
 * procurar parques num raio de centenas de metros.
 */
val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

fun Context.hasLocationPermission(): Boolean = LOCATION_PERMISSIONS.any { permission ->
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
