package pt.vcc.parking.ui.map

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import pt.vcc.parking.R
import pt.vcc.parking.domain.model.Parking
import pt.vcc.parking.location.UserLocation

/**
 * Mapa OpenStreetMap com os parques da seccao 22 do documento do MVP.
 *
 * O osmdroid fica contido neste pacote: o resto da UI troca coordenadas em
 * `Double` e modelos de dominio, nunca `GeoPoint` nem `MapView`.
 */
@Composable
fun ParkingMap(
    userLocation: UserLocation?,
    parking: List<Parking>,
    modifier: Modifier = Modifier,
    onParkingSelected: (Parking) -> Unit = {},
    onCenterChanged: (latitude: Double, longitude: Double) -> Unit = { _, _ -> },
) {
    val mapView = rememberMapViewWithLifecycle()
    val currentOnParkingSelected by rememberUpdatedState(onParkingSelected)
    val currentOnCenterChanged by rememberUpdatedState(onCenterChanged)

    // Sobrevive a rotacao: recentrar apos cada recomposicao anularia o gesto do
    // utilizador sempre que chegasse uma nova leitura do GPS.
    var centeredOnUser by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(mapView) {
        val listener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                mapView.notifyCenter(currentOnCenterChanged)
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                mapView.notifyCenter(currentOnCenterChanged)
                return false
            }
        }
        mapView.addMapListener(listener)

        onDispose { mapView.removeMapListener(listener) }
    }

    LaunchedEffect(mapView, userLocation) {
        val location = userLocation ?: return@LaunchedEffect
        if (centeredOnUser) return@LaunchedEffect

        mapView.controller.setZoom(DEFAULT_ZOOM)
        mapView.controller.setCenter(GeoPoint(location.latitude, location.longitude))
        centeredOnUser = true
        currentOnCenterChanged(location.latitude, location.longitude)
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { map ->
            map.overlays.clear()
            parking.forEach { map.overlays.add(map.parkingMarker(it, currentOnParkingSelected)) }
            // Adicionado por ultimo para ficar por cima dos marcadores dos parques.
            userLocation?.let { map.overlays.add(map.userMarker(it)) }
            map.invalidate()
        },
    )
}

private fun MapView.notifyCenter(onCenterChanged: (Double, Double) -> Unit) {
    val center = mapCenter
    onCenterChanged(center.latitude, center.longitude)
}

private fun MapView.parkingMarker(
    parking: Parking,
    onParkingSelected: (Parking) -> Unit,
): Marker = Marker(this).apply {
    position = GeoPoint(parking.latitude, parking.longitude)
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
    icon = context.drawable(R.drawable.ic_map_parking)
    // O detalhe da seccao 24 substitui a janela de informacao do osmdroid.
    infoWindow = null
    setOnMarkerClickListener { _, _ ->
        onParkingSelected(parking)
        true
    }
}

private fun MapView.userMarker(location: UserLocation): Marker = Marker(this).apply {
    position = GeoPoint(location.latitude, location.longitude)
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    icon = context.drawable(R.drawable.ic_map_user)
    title = context.getString(R.string.parking_user_marker)
    infoWindow = null
    isDraggable = false
    setOnMarkerClickListener { _, _ -> true }
}

private fun Context.drawable(resourceId: Int) = ContextCompat.getDrawable(this, resourceId)
