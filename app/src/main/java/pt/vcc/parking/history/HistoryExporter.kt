package pt.vcc.parking.history

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pt.vcc.parking.domain.model.ParkedCar

/** Formatos de exportacao do historico (`vp-10-history`). */
enum class HistoryExportFormat(val extension: String, val mimeType: String) {
    /** Abre em qualquer visualizador de mapas. */
    GeoJson("geojson", "application/geo+json"),

    /** Folha de calculo; util para justificar despesas. */
    Csv("csv", "text/csv"),
}

/**
 * Serializacao do historico para ficheiro.
 *
 * Exporta sempre o que esta na base de dados no momento, pelo que um registo ja
 * apagado nunca aparece no ficheiro. As datas vao em UTC e ISO-8601 porque o
 * ficheiro pode ser aberto noutro fuso que nao o do telemovel.
 */
object HistoryExporter {

    fun export(entries: List<ParkedCar>, format: HistoryExportFormat): String = when (format) {
        HistoryExportFormat.GeoJson -> geoJson(entries)
        HistoryExportFormat.Csv -> csv(entries)
    }

    fun fileName(format: HistoryExportFormat, millis: Long): String =
        "vcc-parking-historico-${fileStamp(millis)}.${format.extension}"

    /**
     * `FeatureCollection` de pontos: e o que um visualizador comum espera
     * encontrar, e cada registo leva consigo o resto da informacao em
     * `properties`.
     */
    private fun geoJson(entries: List<ParkedCar>): String {
        val features = entries.map { entry ->
            JsonObject(
                mapOf(
                    "type" to JsonPrimitive("Feature"),
                    "geometry" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("Point"),
                            // GeoJSON usa longitude antes da latitude.
                            "coordinates" to JsonArray(
                                listOf(
                                    JsonPrimitive(entry.longitude),
                                    JsonPrimitive(entry.latitude),
                                ),
                            ),
                        ),
                    ),
                    "properties" to JsonObject(
                        mapOf(
                            "id" to JsonPrimitive(entry.id),
                            "parkedAt" to JsonPrimitive(timestamp(entry.parkedAtMillis)),
                            "endedAt" to entry.endedAtMillis.timestampOrNull(),
                            "durationMinutes" to entry.durationMinutes.primitiveOrNull(),
                            "accuracyMeters" to entry.accuracyMeters.primitiveOrNull(),
                            "note" to entry.note.primitiveOrNull(),
                            "parkingId" to entry.parkingId.primitiveOrNull(),
                        ),
                    ),
                ),
            )
        }

        return json.encodeToString(
            JsonObject.serializer(),
            JsonObject(
                mapOf(
                    "type" to JsonPrimitive("FeatureCollection"),
                    "features" to JsonArray(features),
                ),
            ),
        )
    }

    private fun csv(entries: List<ParkedCar>): String = buildString {
        append(CSV_HEADER)
        append(LINE_SEPARATOR)
        entries.forEach { entry ->
            append(
                listOf(
                    entry.id.toString(),
                    timestamp(entry.parkedAtMillis),
                    entry.endedAtMillis?.let(::timestamp).orEmpty(),
                    entry.durationMinutes?.toString().orEmpty(),
                    entry.latitude.toString(),
                    entry.longitude.toString(),
                    entry.accuracyMeters?.toString().orEmpty(),
                    entry.note.orEmpty(),
                    entry.parkingId.orEmpty(),
                ).joinToString(",") { it.escapedForCsv() },
            )
            append(LINE_SEPARATOR)
        }
    }

    private fun timestamp(millis: Long): String = SimpleDateFormat(TIMESTAMP_PATTERN, Locale.ROOT)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(millis))

    private fun fileStamp(millis: Long): String = SimpleDateFormat(FILE_STAMP_PATTERN, Locale.ROOT)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(millis))

    /**
     * Uma nota com virgula, aspas ou mudanca de linha parte o ficheiro em
     * colunas erradas; a regra do RFC 4180 e citar e duplicar as aspas.
     */
    private fun String.escapedForCsv(): String =
        if (none { it in CSV_SPECIAL_CHARACTERS }) this else "\"${replace("\"", "\"\"")}\""

    private fun Long?.timestampOrNull() = this?.let { JsonPrimitive(timestamp(it)) } ?: JsonNull

    private fun Long?.primitiveOrNull() = this?.let { JsonPrimitive(it) } ?: JsonNull

    private fun Float?.primitiveOrNull() = this?.let { JsonPrimitive(it) } ?: JsonNull

    private fun String?.primitiveOrNull() = this?.let { JsonPrimitive(it) } ?: JsonNull

    private val json = Json { prettyPrint = true }

    private const val TIMESTAMP_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    private const val FILE_STAMP_PATTERN = "yyyyMMdd-HHmm"
    private const val LINE_SEPARATOR = "\n"
    private const val CSV_HEADER =
        "id,parkedAt,endedAt,durationMinutes,latitude,longitude,accuracyMeters,note,parkingId"
    private val CSV_SPECIAL_CHARACTERS = charArrayOf(',', '"', '\n', '\r')
}

/** `null` enquanto o estacionamento decorre; no historico ha sempre fim. */
val ParkedCar.durationMillis: Long?
    get() = endedAtMillis?.let { it - parkedAtMillis }

private val ParkedCar.durationMinutes: Long?
    get() = durationMillis?.let { it / MILLIS_PER_MINUTE }

private const val MILLIS_PER_MINUTE = 60_000L
