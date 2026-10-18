package es.sebas1705.haltera.data.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BodyMeasurementsTest {
    @Test
    fun `types are read leniently and only body fat is not a length`() {
        assertEquals(MeasurementType.WAIST, MeasurementType.fromDb("WAIST"))
        assertEquals(MeasurementType.BODY_FAT, MeasurementType.fromDb(" body_fat "))
        assertEquals(MeasurementType.CUSTOM, MeasurementType.fromDb("CALF")) // a newer version's type
        assertEquals(MeasurementType.CUSTOM, MeasurementType.fromDb(null))
        assertEquals(listOf(MeasurementType.BODY_FAT), MeasurementType.entries.filterNot { it.isLength })
    }

    @Test
    fun `custom names are trimmed, collapsed and cut`() {
        assertEquals("Left calf", BodyMeasurements.normalizeName("  Left   calf \t"))
        assertEquals("", BodyMeasurements.normalizeName("   "))
        assertEquals("Muñeca", BodyMeasurements.normalizeName("Muñeca"))
        val long = "a".repeat(100)
        assertEquals(BodyMeasurements.MAX_NAME_LENGTH, BodyMeasurements.normalizeName(long).length)
        // Cutting never leaves a trailing space.
        val spaced = "x".repeat(BodyMeasurements.MAX_NAME_LENGTH - 1) + " yyyy"
        assertEquals("x".repeat(BodyMeasurements.MAX_NAME_LENGTH - 1), BodyMeasurements.normalizeName(spaced))
    }

    @Test
    fun `custom series ignore case and spacing but not accents`() {
        fun custom(name: String) = BodyMeasurement("id", 0, MeasurementType.CUSTOM, 300, customName = name)
        assertEquals(custom("Calf").seriesKey, custom("  calf ").seriesKey)
        assertEquals(custom("Left calf").seriesKey, custom("LEFT   CALF").seriesKey)
        assertNotEquals(custom("Muñeca").seriesKey, custom("Muneca").seriesKey)
        assertEquals("WAIST", BodyMeasurement("id", 0, MeasurementType.WAIST, 800).seriesKey)
        assertNotEquals("WAIST", custom("WAIST").seriesKey)
    }

    @Test
    fun `values must be positive and plausible`() {
        assertFalse(BodyMeasurements.isValidValue(MeasurementType.WAIST, 0))
        assertFalse(BodyMeasurements.isValidValue(MeasurementType.WAIST, -5))
        assertTrue(BodyMeasurements.isValidValue(MeasurementType.WAIST, 1))
        assertTrue(BodyMeasurements.isValidValue(MeasurementType.CUSTOM, BodyMeasurements.MAX_LENGTH_MM))
        assertFalse(BodyMeasurements.isValidValue(MeasurementType.ARM, BodyMeasurements.MAX_LENGTH_MM + 1))
        assertTrue(BodyMeasurements.isValidValue(MeasurementType.BODY_FAT, 1))
        assertTrue(BodyMeasurements.isValidValue(MeasurementType.BODY_FAT, 1_000))
        assertFalse(BodyMeasurements.isValidValue(MeasurementType.BODY_FAT, 1_001))
        assertFalse(BodyMeasurements.isValidValue(MeasurementType.BODY_FAT, 0))
    }
}
