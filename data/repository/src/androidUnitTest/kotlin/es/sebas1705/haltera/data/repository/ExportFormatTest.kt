package es.sebas1705.haltera.data.repository

import es.sebas1705.haltera.data.models.BodyMeasurement
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.MeasurementType
import kotlinx.datetime.TimeZone
import es.sebas1705.haltera.data.repository.export.ByteArrayOutput
import es.sebas1705.haltera.data.repository.export.CsvWriter
import es.sebas1705.haltera.data.repository.export.ExportFormat
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class ExportFormatTest {
    @Test
    fun `grams become kilograms exactly, with a point and no trailing zeros`() {
        assertEquals("0", ExportFormat.kilograms(0))
        assertEquals("82.5", ExportFormat.kilograms(82_500))
        assertEquals("100", ExportFormat.kilograms(100_000))
        assertEquals("60.125", ExportFormat.kilograms(60_125))
        assertEquals("0.001", ExportFormat.kilograms(1))
        assertEquals("-12.5", ExportFormat.kilograms(-12_500))
        assertEquals("-0.5", ExportFormat.kilograms(-500))
    }

    @Test
    fun `decimals are rounded, never use an exponent or a comma`() {
        assertEquals("8.5", ExportFormat.decimal(8.5, 2))
        assertEquals("10", ExportFormat.decimal(10.0, 2))
        assertEquals("33.33", ExportFormat.decimal(33.333, 2))
        assertEquals("0.01", ExportFormat.decimal(0.0123, 2))
        assertEquals("0", ExportFormat.decimal(0.0001, 2))
        assertEquals("-1.5", ExportFormat.decimal(-1.5, 2))
        assertEquals("", ExportFormat.decimal(Double.NaN, 2))
    }

    @Test
    fun `numbers ignore the default locale`() {
        val reference = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("es-ES"))
            assertEquals("82.5", ExportFormat.kilograms(82_500))
            assertEquals("8.5", ExportFormat.decimal(8.5, 2))
        } finally {
            Locale.setDefault(reference)
        }
    }

    @Test
    fun `the load unit follows the load type and what was typed`() {
        assertEquals("kg", ExportFormat.loadUnit(LoadType.BARBELL, """{"type":"barbell","barGrams":20000}"""))
        assertEquals("kg", ExportFormat.loadUnit(null, null))
        assertEquals("plate", ExportFormat.loadUnit(LoadType.MACHINE, """{"type":"machinePlate"}"""))
        assertEquals("kg", ExportFormat.loadUnit(LoadType.MACHINE, """{"type":"machineWeight"}"""))
        assertEquals("level", ExportFormat.loadUnit(LoadType.BAND, null))
        assertEquals("kg", ExportFormat.loadUnit(LoadType.DUMBBELL, "not json"))
    }

    @Test
    fun `csv fields are quoted only when they need it`() {
        val out = ByteArrayOutput()
        val csv = CsvWriter(out, listOf("a", "b"))
        csv.row(listOf("plain", "with, comma"))
        csv.row(listOf("say \"hi\"", "two\nlines"))
        csv.row(listOf("", "ñandú"))

        assertEquals(
            "\uFEFFa,b\r\nplain,\"with, comma\"\r\n\"say \"\"hi\"\"\",\"two\nlines\"\r\n,ñandú\r\n",
            out.toByteArray().decodeToString(),
        )
        assertEquals(3, csv.rows)
    }

    @Test
    fun `a measurement row is centimetres or percent, exact, with the stored value and the local date`() {
        val zone = TimeZone.of("Europe/Madrid")
        // Already the 16th in Madrid.
        val at = java.time.Instant.parse("2026-10-15T23:30:00Z").toEpochMilli()
        assertEquals(
            listOf("2026-10-16", "2026-10-15T23:30:00Z", "WAIST", "", "82.5", "cm", "825", "note", "m1"),
            ExportFormat.bodyMeasurementRow(
                BodyMeasurement("m1", at, MeasurementType.WAIST, 825, notes = "note"),
                zone
            ),
        )
        assertEquals(
            listOf("18.5", "percent", "185"),
            ExportFormat.bodyMeasurementRow(
                BodyMeasurement("m2", at, MeasurementType.BODY_FAT, 185),
                zone
            ).subList(4, 7),
        )
        assertEquals(
            listOf("CUSTOM", "Calf", "38", "cm", "380"),
            ExportFormat.bodyMeasurementRow(
                BodyMeasurement("m3", at, MeasurementType.CUSTOM, 380, "Calf"),
                zone
            ).subList(2, 7),
        )
        assertEquals("0.1", ExportFormat.bodyMeasurementRow(BodyMeasurement("m4", at, MeasurementType.ARM, 1), zone)[4])
        assertEquals(
            "500",
            ExportFormat.bodyMeasurementRow(BodyMeasurement("m5", at, MeasurementType.ARM, 5_000), zone)[4]
        )
    }
}
