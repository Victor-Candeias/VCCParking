package pt.vcc.parking.location

/**
 * Posição do utilizador independente do SDK Android, para que o domínio e os testes
 * não dependam de `android.location.Location`.
 */
data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val timestampMillis: Long,
)
