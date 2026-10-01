package pt.vcc.parking.domain.model

/**
 * Registo do local onde o utilizador deixou o carro (`vp-08-park-save`).
 *
 * Ao contrario de [Parking], que e cache descartavel vinda do Overpass, isto e
 * informacao do proprio utilizador: nunca e apagada, apenas terminada com
 * [endedAtMillis], porque o historico de `vp-10-history` depende dos registos
 * anteriores continuarem a existir.
 */
data class ParkedCar(
    val id: Long = NO_ID,
    val latitude: Double,
    val longitude: Double,
    /** `null` quando a posicao foi marcada a mao e nao medida pelo GPS. */
    val accuracyMeters: Float? = null,
    val parkedAtMillis: Long,
    /** `null` enquanto o estacionamento esta a decorrer. */
    val endedAtMillis: Long? = null,
    val note: String? = null,
    val photoUri: String? = null,
    val osmType: String? = null,
    val osmId: Long? = null,
) {

    val isActive: Boolean get() = endedAtMillis == null

    /** Mesma identidade de [Parking.id]; `null` quando o registo nao nasceu de um parque. */
    val parkingId: String? get() =
        if (osmType != null && osmId != null) "$osmType/$osmId" else null

    /**
     * Uma posicao marcada a mao nao tem precisao medida, mas e exatamente onde o
     * utilizador disse que estava — nao ha incerteza a avisar.
     */
    val isAccurate: Boolean get() =
        accuracyMeters == null || accuracyMeters <= GOOD_ACCURACY_METERS

    companion object {
        /** Antes de o Room atribuir a chave primaria. */
        const val NO_ID = 0L

        /**
         * Acima disto o ponto no mapa cobre mais do que um quarteirao e mostra-lo
         * sem aviso daria a entender uma certeza que a leitura nao tem.
         */
        const val GOOD_ACCURACY_METERS = 30f
    }
}
