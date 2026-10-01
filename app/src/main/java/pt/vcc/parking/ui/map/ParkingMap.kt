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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import pt.vcc.parking.R
import pt.vcc.parking.domain.model.ParkedCar
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
    parkedCar: ParkedCar? = null,
    initialCenter: MapPoint? = null,
    routePoints: List<MapPoint> = emptyList(),
    onParkingSelected: (Parking) -> Unit = {},
    onParkedCarSelected: () -> Unit = {},
    onCenterChanged: (latitude: Double, longitude: Double) -> Unit = { _, _ -> },
) {
    val mapView = rememberMapViewWithLifecycle()
    val currentOnParkingSelected by rememberUpdatedState(onParkingSelected)
    val currentOnParkedCarSelected by rememberUpdatedState(onParkedCarSelected)
    val currentOnCenterChanged by rememberUpdatedState(onCenterChanged)

    // Sobrevive a rotacao: recentrar apos cada recomposicao anularia o gesto do
    // utilizador sempre que chegasse uma nova leitura do GPS.
    var centered by rememberSaveable { mutableStateOf(false) }

    // Quem abre o mapa a escolher um ponto ja sabe onde quer comecar; so na
    // falta desse ponto e que a posicao do utilizador serve de centro.
    val center = initialCenter ?: userLocation?.let { MapPoint(it.latitude, it.longitude) }

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

    LaunchedEffect(mapView, center) {
        val point = center ?: return@LaunchedEffect
        if (centered) return@LaunchedEffect

        mapView.controller.setZoom(DEFAULT_ZOOM)
        mapView.controller.setCenter(GeoPoint(point.latitude, point.longitude))
        centered = true
        currentOnCenterChanged(point.latitude, point.longitude)
    }

    AndroidView(
        // O MapView desenha os tiles para la da area medida e tapava o conteudo
        // seguinte; o recorte mantem o mapa dentro do espaco que lhe foi dado.
        modifier = modifier.clipToBounds(),
        factory = { mapView },
        update = { map ->
            map.overlays.clear()
            // A linha vai por baixo de tudo: o que interessa tocar sao os pinos.
            if (routePoints.size >= MINIMUM_ROUTE_POINTS) {
                map.overlays.add(map.routeOverlay(routePoints))
            }
            parking.forEach { map.overlays.add(map.parkingMarker(it, currentOnParkingSelected)) }
            // Adicionados por ultimo para ficarem por cima dos marcadores dos parques.
            parkedCar?.let { map.overlays.add(map.parkedCarMarker(it, currentOnParkedCarSelected)) }
            userLocation?.let { map.overlays.add(map.userMarker(it)) }
            map.invalidate()
        },
    )
}

/** Ponto do mapa em coordenadas simples, para o osmdroid nao sair deste pacote. */
data class MapPoint(val latitude: Double, val longitude: Double)

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

/**
 * O carro usa um marcador proprio e e clicavel: no meio de varios pinos de
 * parques, o unico que interessa ao regressar e este.
 */
private fun MapView.parkedCarMarker(
    parkedCar: ParkedCar,
    onParkedCarSelected: () -> Unit,
): Marker = Marker(this).apply {
    position = GeoPoint(parkedCar.latitude, parkedCar.longitude)
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
    icon = context.drawable(R.drawable.ic_map_car)
    title = context.getString(R.string.parked_marker)
    infoWindow = null
    setOnMarkerClickListener { _, _ ->
        onParkedCarSelected()
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

/** Uma linha precisa de dois pontos; com menos nao ha nada para desenhar. */
private const val MINIMUM_ROUTE_POINTS = 2
