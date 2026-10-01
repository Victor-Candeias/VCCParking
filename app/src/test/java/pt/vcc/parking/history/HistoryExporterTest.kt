package pt.vcc.parking.history

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.vcc.parking.domain.model.ParkedCar

class HistoryExporterTest {

    @Test
    fun `the geojson is a feature collection of points`() {
        val content = HistoryExporter.export(listOf(entry()), HistoryExportFormat.GeoJson)
        val root = Json.parseToJsonElement(content).jsonObject

        assertEquals("FeatureCollection", root.string("type"))

        val feature = root.array("features").single().jsonObject
        assertEquals("Feature", feature.string("type"))

        val geometry = feature.obj("geometry")
        assertEquals("Point", geometry.string("type"))

        // GeoJSON exige longitude antes da latitude.
        val coordinates = geometry.array("coordinates")
        assertEquals(LONGITUDE, coordinates[0].jsonPrimitive.content.toDouble(), 1e-9)
        assertEquals(LATITUDE, coordinates[1].jsonPrimitive.content.toDouble(), 1e-9)
    }

    @Test
    fun `the timestamps are written in utc`() {
        val content = HistoryExporter.export(listOf(entry()), HistoryExportFormat.GeoJson)
        val properties = Json.parseToJsonElement(content)
            .jsonObject.array("features").single().jsonObject.obj("properties")

        assertEquals("2023-11-14T22:13:20Z", properties.string("parkedAt"))
        assertEquals("2023-11-14T23:13:20Z", properties.string("endedAt"))
        assertEquals("60", properties.string("durationMinutes"))
    }

    @Test
    fun `the csv starts with the header and one line per record`() {
        val content = HistoryExporter.export(
            listOf(entry(), entry(id = 2)),
            HistoryExportFormat.Csv,
        )
        val lines = content.trim().lines()

        assertTrue(lines.first().startsWith("id,parkedAt,endedAt"))
        assertEquals(3, lines.size)
    }

    @Test
    fun `a note with a comma does not break the columns`() {
        val content = HistoryExporter.export(
            listOf(entry(note = "Piso -2, lugar \"134\"")),
            HistoryExportFormat.Csv,
        )

        assertTrue(content.contains("\"Piso -2, lugar \"\"134\"\"\""))
    }

    @Test
    fun `an empty history still produces a valid file`() {
        val geoJson = HistoryExporter.export(emptyList(), HistoryExportFormat.GeoJson)
        assertEquals(0, Json.parseToJsonElement(geoJson).jsonObject.array("features").size)

        val csv = HistoryExporter.export(emptyList(), HistoryExportFormat.Csv)
        assertEquals(1, csv.trim().lines().size)
    }

    @Test
    fun `the suggested name carries the format and the date`() {
        assertEquals(
            "vcc-parking-historico-20231114-2213.geojson",
            HistoryExporter.fileName(HistoryExportFormat.GeoJson, PARKED_AT_MILLIS),
        )
        assertTrue(
            HistoryExporter.fileName(HistoryExportFormat.Csv, PARKED_AT_MILLIS)
                .endsWith(".csv"),
        )
    }

    private fun entry(id: Long = 1, note: String? = null) = ParkedCar(
        id = id,
        latitude = LATITUDE,
        longitude = LONGITUDE,
        parkedAtMillis = PARKED_AT_MILLIS,
        endedAtMillis = PARKED_AT_MILLIS + ONE_HOUR_MILLIS,
        note = note,
    )

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject

    private fun JsonObject.array(key: String): JsonArray = getValue(key).jsonArray

    private companion object {
        const val LATITUDE = 38.7336
        const val LONGITUDE = -9.1447

        /** 2023-11-14T22:13:20Z. */
        const val PARKED_AT_MILLIS = 1_700_000_000_000L
        const val ONE_HOUR_MILLIS = 3_600_000L
    }
}
