package es.sebas1705.haltera.data.repository

import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.SetKind
import es.sebas1705.haltera.data.repository.importer.CsvReader
import es.sebas1705.haltera.data.repository.importer.ImportValues
import es.sebas1705.haltera.data.repository.importer.ImportWeightUnit
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportValuesTest {

    // ----- numbers -----

    @Test
    fun `decimals accept points, commas, separators and spaces`() {
        assertEquals(82.5, ImportValues.decimal("82.5"))
        assertEquals(82.5, ImportValues.decimal(" 82,5 "))
        assertEquals(82.0, ImportValues.decimal("82"))
        assertEquals(-12.5, ImportValues.decimal("-12,5"))
        assertEquals(1234567.0, ImportValues.decimal("1.234.567"), "several points are thousands")
        assertEquals(1234567.0, ImportValues.decimal("1,234,567"), "several commas are thousands")
        assertEquals(1234.5, ImportValues.decimal("1 234,5"))
        assertEquals(1234.5, ImportValues.decimal("1 234,5"))
        assertEquals(12.25, ImportValues.decimal("12,25"))
        assertEquals(0.125, ImportValues.decimal("0,125"))
        assertEquals(1250.0, ImportValues.decimal("1,250"))
    }

    @Test
    fun `decimals reject what is not a number`() {
        assertNull(ImportValues.decimal(null))
        assertNull(ImportValues.decimal(""))
        assertNull(ImportValues.decimal("   "))
        assertNull(ImportValues.decimal("abc"))
        assertNull(ImportValues.decimal("NaN"))
        assertNull(ImportValues.decimal("Infinity"))
        assertNull(ImportValues.decimal("12kg"))
    }

    @Test
    fun `integers are non negative and rounded`() {
        assertEquals(5, ImportValues.integer("5"))
        assertEquals(5, ImportValues.integer("5.0"))
        assertEquals(6, ImportValues.integer("5.6"))
        assertEquals(0, ImportValues.integer("0"))
        assertNull(ImportValues.integer("-1"))
        assertNull(ImportValues.integer("9999999999"))
        assertNull(ImportValues.integer(""))
    }

    @Test
    fun `rpe is kept only between 0 exclusive and 10`() {
        assertEquals(10.0, ImportValues.rpe("10"))
        assertEquals(7.5, ImportValues.rpe("7,5"))
        assertEquals(0.5, ImportValues.rpe("0.5"))
        assertNull(ImportValues.rpe("0"))
        assertNull(ImportValues.rpe("10.5"))
        assertNull(ImportValues.rpe("-1"))
        assertNull(ImportValues.rpe(""))
    }

    @Test
    fun `grams convert kilograms exactly and pounds with the legal factor`() {
        assertEquals(82_500L, ImportValues.grams(82.5, ImportWeightUnit.KG))
        assertEquals(60_125L, ImportValues.grams(60.125, ImportWeightUnit.KG))
        assertEquals(45_359L, ImportValues.grams(100.0, ImportWeightUnit.LB))
        assertEquals(454L, ImportValues.grams(1.0, ImportWeightUnit.LB))
        assertEquals(-10_000L, ImportValues.grams(-10.0, ImportWeightUnit.KG))
        assertEquals(0L, ImportValues.grams(0.0, ImportWeightUnit.LB))
    }

    @Test
    fun `weight units are read from their usual spellings`() {
        listOf("kg", "kgs", "KG", " Kgs ", "kilograms").forEach {
            assertEquals(ImportWeightUnit.KG, ImportValues.weightUnit(it), it)
        }
        listOf(
            "lb",
            "lbs",
            "LBS",
            "pounds"
        ).forEach { assertEquals(ImportWeightUnit.LB, ImportValues.weightUnit(it), it) }
        assertNull(ImportValues.weightUnit("st"))
        assertNull(ImportValues.weightUnit(""))
        assertNull(ImportValues.weightUnit(null))
    }

    @Test
    fun `distances become whole metres`() {
        assertEquals(5250, ImportValues.meters(5.25, "km"))
        assertEquals(1609, ImportValues.meters(1.0, "mi"))
        assertEquals(1609, ImportValues.meters(1.0, "Miles"))
        assertEquals(30, ImportValues.meters(100.0, "ft"))
        assertEquals(91, ImportValues.meters(100.0, "yd"))
        assertEquals(400, ImportValues.meters(400.0, "m"))
        assertEquals(400, ImportValues.meters(400.0, ""), "no unit is metres")
        assertEquals(400, ImportValues.meters(400.0, null))
    }

    // ----- durations -----

    @Test
    fun `durations in every exporter format`() {
        assertEquals(3900, ImportValues.durationSeconds("1h 5m"))
        assertEquals(3903, ImportValues.durationSeconds("1h 5m 3s"))
        assertEquals(3120, ImportValues.durationSeconds("52m"))
        assertEquals(3120, ImportValues.durationSeconds("52 min"))
        assertEquals(200, ImportValues.durationSeconds("200s"))
        assertEquals(200, ImportValues.durationSeconds("200 sec"))
        assertEquals(3900, ImportValues.durationSeconds("1:05:00"))
        assertEquals(330, ImportValues.durationSeconds("05:30"))
        assertEquals(1530, ImportValues.durationSeconds("00:25:30"))
        assertEquals(90, ImportValues.durationSeconds("90"))
        assertEquals(90, ImportValues.durationSeconds("1.5m"))
        assertEquals(0, ImportValues.durationSeconds("0"))
    }

    @Test
    fun `durations that make no sense are null`() {
        assertNull(ImportValues.durationSeconds(null))
        assertNull(ImportValues.durationSeconds(""))
        assertNull(ImportValues.durationSeconds("soon"))
        assertNull(ImportValues.durationSeconds("1:2:3:4"))
        assertNull(ImportValues.durationSeconds("a:b"))
        assertNull(ImportValues.durationSeconds("-5"))
    }

    // ----- dates -----

    private fun date(raw: String?) = ImportValues.whenOf(raw)

    @Test
    fun `dates keep the time when the file has one and null when not`() {
        val withTime = date("2025-04-29 18:05:07")!!
        assertEquals(LocalDate(2025, 4, 29), withTime.date)
        assertEquals(18, withTime.hour)
        assertEquals(5, withTime.minute)
        assertEquals(7, withTime.second)
        val dayOnly = date("2025-04-29")!!
        assertNull(dayOnly.hour)
        assertEquals(12, dayOnly.toLocalDateTime(12).hour)
    }

    @Test
    fun `month names in the languages the exporters use`() {
        val expected = LocalDate(2025, 3, 4)
        listOf(
            "4 Mar 2025", "4 mar 2025", "4 mars 2025", "4 März 2025", "4 Mrz. 2025", "4 maart 2025", "4 mrt 2025",
            "4 marzo 2025", "Mar 4, 2025", "March 4, 2025",
        ).forEach { assertEquals(expected, date(it)?.date, it) }
        assertEquals(LocalDate(2025, 8, 1), date("1 août 2025")?.date)
        assertEquals(LocalDate(2025, 12, 24), date("24 dic 2025, 20:00")?.date)
        assertEquals(LocalDate(2025, 10, 9), date("9 okt 2025")?.date)
        assertEquals(LocalDate(2025, 1, 2), date("2 gen 2025")?.date)
        assertEquals(LocalDate(2025, 2, 2), date("2 févr. 2025")?.date)
        assertNull(date("4 Foo 2025"))
    }

    @Test
    fun `numeric dates are day first unless that is impossible`() {
        assertEquals(LocalDate(2025, 4, 3), date("03/04/2025")?.date)
        assertEquals(LocalDate(2025, 4, 29), date("29/04/2025")?.date)
        assertEquals(LocalDate(2025, 4, 29), date("04/29/2025")?.date)
        assertEquals(LocalDate(2025, 4, 29), date("29.04.2025")?.date)
        assertEquals(LocalDate(2025, 4, 29), date("29-04-25")?.date)
        assertEquals(LocalDate(2025, 4, 29), date("2025/04/29")?.date)
    }

    @Test
    fun `impossible or implausible dates are rejected`() {
        assertEquals(LocalDate(2024, 2, 29), date("2024-02-29")?.date, "leap day")
        assertNull(date("2025-02-29"))
        assertNull(date("2025-13-01"))
        assertNull(date("2025-00-10"))
        assertNull(date("31/02/2025"))
        assertNull(date("2025-04-29 24:00"))
        assertNull(date("2025-04-29 10:60"))
        assertNull(date("1900-01-01"))
        assertNull(date("2300-01-01"))
        assertNull(date(""))
        assertNull(date(null))
        assertNull(date("yesterday"))
    }

    @Test
    fun `future and old dates in range are kept as written`() {
        assertEquals(LocalDate(2030, 1, 1), date("2030-01-01")?.date)
        assertEquals(LocalDate(1990, 6, 15), date("1990-06-15")?.date)
    }

    // ----- set kinds, equipment, keys, ids -----

    @Test
    fun `set kinds of every source`() {
        mapOf(
            "warmup" to SetKind.WARMUP, "Warm-up" to SetKind.WARMUP, "W" to SetKind.WARMUP, "w" to SetKind.WARMUP,
            "dropset" to SetKind.DROP, "Drop set" to SetKind.DROP, "D" to SetKind.DROP,
            "failure" to SetKind.FAILURE, "F" to SetKind.FAILURE, "failed" to SetKind.FAILURE,
            "normal" to SetKind.WORKING, "Working" to SetKind.WORKING, "" to SetKind.WORKING, "1" to SetKind.WORKING,
            "x" to SetKind.WORKING,
        ).forEach { (raw, kind) -> assertEquals(kind, ImportValues.setKind(raw), raw) }
        assertEquals(SetKind.WORKING, ImportValues.setKind(null))
    }

    @Test
    fun `equipment words and phrases`() {
        assertEquals(Equipment.BARBELL, ImportValues.equipmentOf("Barbell"))
        assertEquals(Equipment.DUMBBELL, ImportValues.equipmentOf("dumbbells"))
        assertEquals(Equipment.SMITH, ImportValues.equipmentOf("Smith Machine"))
        assertEquals(Equipment.CABLE, ImportValues.equipmentOf("Pulley"))
        assertEquals(Equipment.EZ_BAR, ImportValues.equipmentOf("EZ Bar"))
        assertEquals(Equipment.TRAP_BAR, ImportValues.equipmentOf("hex bar"))
        assertEquals(Equipment.MACHINE, ImportValues.equipmentOf("máquina"))
        assertEquals(Equipment.BODYWEIGHT, ImportValues.equipmentOf("Bodyweight"))
        assertNull(ImportValues.equipmentOf("Assisted"))
        assertNull(ImportValues.equipmentOf("Weighted"))
        assertNull(ImportValues.equipmentOf("Rope"))
        assertNull(ImportValues.equipmentOf(null))
    }

    @Test
    fun `match keys ignore case, accents, punctuation and spacing`() {
        assertEquals("pull up", ImportValues.matchKey("Pull-Up"))
        assertEquals("pull up", ImportValues.matchKey("  PULL   up "))
        assertEquals("press de banca", ImportValues.matchKey("Press de Bánca"))
        assertEquals("bench press barbell", ImportValues.matchKey("Bench Press (Barbell)"))
        assertEquals("leg press 45", ImportValues.matchKey("Leg press, 45°"))
        assertEquals("", ImportValues.matchKey("  --  "))
    }

    @Test
    fun `stable ids are deterministic, uuid shaped and sensitive to every part`() {
        val a = ImportValues.stableId("HEVY", "2025-04-29T18:00", "0")
        assertEquals(a, ImportValues.stableId("HEVY", "2025-04-29T18:00", "0"))
        assertTrue(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(a))
        assertNotEquals(a, ImportValues.stableId("STRONG", "2025-04-29T18:00", "0"))
        assertNotEquals(a, ImportValues.stableId("HEVY", "2025-04-29T18:01", "0"))
        assertNotEquals(a, ImportValues.stableId("HEVY", "2025-04-29T18:00", "1"))
        // The separator keeps "a"+"bc" and "ab"+"c" apart.
        assertNotEquals(ImportValues.stableId("a", "bc"), ImportValues.stableId("ab", "c"))
        // Pinned value: changing the hash would duplicate every session imported before.
        assertEquals("52e4be05-1d1c-11ba-5a1d-dc2435b1f1bf", a)
        val ids = (0 until 20_000).map { ImportValues.stableId("HEVY", "2025-01-01T00:00", it.toString()) }.toSet()
        assertEquals(20_000, ids.size)
    }

    // ----- CSV reader -----

    private fun rows(text: String, delimiter: Char = ','): List<List<String>> {
        val reader = CsvReader(text, delimiter)
        return generateSequence { reader.next() }.toList()
    }

    @Test
    fun `csv reader handles quotes, empty lines and every line end`() {
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), rows("a,b\n\n1,2\n"))
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), rows("a,b\r\n1,2\r\n"))
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), rows("a,b\r1,2"))
        assertEquals(
            listOf(listOf("x,y", "say \"hi\"", "line\nbreak")),
            rows("\"x,y\",\"say \"\"hi\"\"\",\"line\nbreak\"")
        )
        assertEquals(listOf(listOf("a", "", "")), rows("a,,"))
        assertEquals(listOf(listOf("5\" plate")), rows("5\" plate"))
        assertEquals(listOf(listOf("a", "b")), rows("﻿a,b"))
        assertEquals(emptyList(), rows(""))
        assertEquals(emptyList(), rows("﻿"))
        assertEquals(emptyList(), rows("\n\r\n\n"))
        assertEquals(listOf(listOf("unterminated")), rows("\"unterminated"))
    }

    @Test
    fun `the delimiter comes from the header outside quotes`() {
        assertEquals(',', CsvReader.detectDelimiter("a,b,c\n1;2;3;4;5"))
        assertEquals(';', CsvReader.detectDelimiter("a;b;c\n1,2"))
        assertEquals('\t', CsvReader.detectDelimiter("a\tb\tc"))
        assertEquals(';', CsvReader.detectDelimiter("\"a,b\";c;d"))
        assertEquals(',', CsvReader.detectDelimiter("single"))
        assertEquals(',', CsvReader.detectDelimiter(""))
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), rows("a\tb\n1\t2", '\t'))
    }
}
