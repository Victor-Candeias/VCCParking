package pt.vcc.parking.ui

/** Raios da seccao 20 do documento do MVP. */
object SearchRadius {

    /** O ecra principal foca o estacionamento a volta do utilizador (vp-18). */
    const val DEFAULT_METERS = 500

    val OPTIONS_METERS = listOf(500, 1_000, 2_000, 5_000)
}
