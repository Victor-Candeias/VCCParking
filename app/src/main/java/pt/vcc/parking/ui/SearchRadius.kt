package pt.vcc.parking.ui

import pt.vcc.parking.data.remote.OverpassQuery

/** Raios da seccao 20 do documento do MVP. */
object SearchRadius {

    const val DEFAULT_METERS = OverpassQuery.DEFAULT_RADIUS_METERS

    val OPTIONS_METERS = listOf(500, 1_000, 2_000, 5_000)
}
