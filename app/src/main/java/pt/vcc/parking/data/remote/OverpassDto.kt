package pt.vcc.parking.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class OverpassResponse(
    val elements: List<OverpassElement> = emptyList(),
)

@Serializable
data class OverpassElement(
    val type: String,
    val id: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    val center: OverpassCenter? = null,
    val tags: Map<String, String> = emptyMap(),
) {
    /** Nodes trazem `lat`; ways e relations trazem o centroide em `center`. */
    val latitude: Double? get() = lat ?: center?.lat

    val longitude: Double? get() = lon ?: center?.lon

    val hasCoordinates: Boolean get() = latitude != null && longitude != null
}

@Serializable
data class OverpassCenter(
    val lat: Double,
    val lon: Double,
)

internal val overpassJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
