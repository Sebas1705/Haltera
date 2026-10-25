package es.sebas1705.haltera.data.repository

import es.sebas1705.haltera.data.models.LoadType
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
}
