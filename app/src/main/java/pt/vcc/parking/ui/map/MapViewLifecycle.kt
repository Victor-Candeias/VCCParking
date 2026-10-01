package pt.vcc.parking.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import pt.vcc.parking.R

/** Zoom de rua: cobre o raio de 1 km sugerido na seccao 20. */
internal const val DEFAULT_ZOOM = 15.0

/** Centro do exemplo da seccao 3, usado ate haver localizacao. */
internal const val FALLBACK_LATITUDE = 38.7253
internal const val FALLBACK_LONGITUDE = -9.1500

/**
 * Cria um [MapView] ligado ao ciclo de vida do ecra.
 *
 * O osmdroid tem threads de tiles proprias: sem `onPause` continuaria a descarregar
 * em segundo plano e sem `onDetach` deixaria o `MapView` retido apos a saida.
 */
@Composable
internal fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val description = stringResource(R.string.parking_map_description)
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            // Os botoes legados sobrepoem-se aos controlos Compose; o gesto chega.
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(DEFAULT_ZOOM)
            controller.setCenter(GeoPoint(FALLBACK_LATITUDE, FALLBACK_LONGITUDE))
            contentDescription = description
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    return mapView
}
