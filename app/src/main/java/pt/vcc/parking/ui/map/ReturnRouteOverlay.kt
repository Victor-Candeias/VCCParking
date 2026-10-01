package pt.vcc.parking.ui.map

import android.graphics.Color
import android.graphics.Paint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

/**
 * Linha da rota pedonal sobre o mapa (`vp-09-return-route`).
 *
 * Vive em `ui.map` e nao no pacote do regresso para manter a regra de
 * [ParkingMap]: o osmdroid nao sai deste pacote, e o resto da UI continua a
 * trocar apenas coordenadas em `Double`.
 */
internal fun MapView.routeOverlay(points: List<MapPoint>): Polyline = Polyline(this).apply {
    setPoints(points.map { GeoPoint(it.latitude, it.longitude) })

    // O mesmo azul do pino do carro: a linha e o caminho ate ele, nao um
    // elemento novo a competir com os pinos vermelhos dos parques.
    outlinePaint.color = ROUTE_COLOR
    outlinePaint.strokeWidth = ROUTE_STROKE_WIDTH
    outlinePaint.strokeCap = Paint.Cap.ROUND
    outlinePaint.strokeJoin = Paint.Join.ROUND
    outlinePaint.isAntiAlias = true

    infoWindow = null
    setOnClickListener { _, _, _ -> true }
}

private val ROUTE_COLOR = Color.parseColor("#1D4ED8")

/** Suficiente para se ler sobre os tiles sem tapar o traçado das ruas. */
private const val ROUTE_STROKE_WIDTH = 10f
