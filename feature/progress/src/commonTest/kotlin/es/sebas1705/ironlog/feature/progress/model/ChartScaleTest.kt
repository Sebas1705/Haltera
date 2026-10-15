package es.sebas1705.ironlog.feature.progress.model

import es.sebas1705.ironlog.core.units.WeightUnit
import es.sebas1705.ironlog.feature.progress.ui.components.UnitFormat
import es.sebas1705.ironlog.feature.progress.ui.components.fillPattern
import es.sebas1705.ironlog.feature.progress.ui.components.formatNumber
import es.sebas1705.ironlog.feature.progress.ui.components.signed
import es.sebas1705.ironlog.feature.progress.ui.components.signedPercent
import kotlin.test.Test
import kotlin.test.assertEquals

class ChartScaleTest {

    @Test
    fun `nice scales use 1, 2, 2,5 or 5 steps`() {
        assertEquals(AxisScale(0.0, 15_000.0, 5_000.0), ChartScale.nice(0.0, 12_340.0, includeZero = true))
        assertEquals(listOf(0.0, 5_000.0, 10_000.0, 15_000.0), AxisScale(0.0, 15_000.0, 5_000.0).ticks)
        assertEquals(AxisScale(80.0, 90.0, 2.5), ChartScale.nice(81.2, 89.6))
        assertEquals(AxisScale(0.0, 4.0, 1.0), ChartScale.nice(0.0, 3.4, includeZero = true))
        assertEquals(AxisScale(0.0, 1.0, 0.25), ChartScale.nice(0.0, 0.0, includeZero = true))
        // A flat line is padded around its value.
        val flat = ChartScale.nice(100.0, 100.0)
        assertEquals(true, flat.min < 100.0 && flat.max > 100.0)
        assertEquals(0.5, flat.fraction(100.0), 1e-9)
    }

    @Test
    fun `label indices spread evenly and keep both ends`() {
        assertEquals(listOf(0, 1, 2), ChartScale.labelIndices(3, 4))
        assertEquals(listOf(0, 4, 8, 12), ChartScale.labelIndices(13, 4))
        assertEquals(listOf(0, 17, 34, 51), ChartScale.labelIndices(52, 4))
        assertEquals(listOf(9), ChartScale.labelIndices(10, 1))
        assertEquals(emptyList(), ChartScale.labelIndices(0, 4))
    }

    @Test
    fun `compact and trimmed numbers`() {
        assertEquals("950", ChartScale.compact(950.0))
        assertEquals("1.5k", ChartScale.compact(1_500.0))
        assertEquals("12k", ChartScale.compact(12_400.0))
        assertEquals("1.2M", ChartScale.compact(1_200_000.0))
        assertEquals("62.5", ChartScale.trim(62.5))
        assertEquals("62", ChartScale.trim(62.0))
        assertEquals("2.5", ChartScale.trim(2.5, 2))
    }

    @Test
    fun `numbers, signs and patterns`() {
        assertEquals("1234.5", formatNumber(1_234.5, 1))
        assertEquals("12 400", formatNumber(12_400.0, 0))
        assertEquals("−3.0", formatNumber(-3.0, 1))
        assertEquals("0", formatNumber(-0.2, 0))
        assertEquals("+2.5 kg", signed(2.5) { "${ChartScale.trim(it)} kg" })
        assertEquals("−2.5 kg", signed(-2.5) { "${ChartScale.trim(it)} kg" })
        assertEquals("+20 %", signedPercent(20.0))
        assertEquals("14 Oct", fillPattern("%2\$s %1\$s", "Oct", "14"))
        assertEquals("3 × 5", fillPattern("%1\$s × %2\$d", "3", "5"))
    }

    @Test
    fun `unit formats convert grams for display`() {
        val kg = UnitFormat(WeightUnit.KG, "kg")
        assertEquals(100.0, kg.toUnit(100_000.0))
        assertEquals("62.5 kg", kg.weight(62_500.0))
        assertEquals("12 400 kg", kg.volume(12_400_000.0))
        val lb = UnitFormat(WeightUnit.LB, "lb")
        assertEquals(220.462, lb.toUnit(100_000.0), 0.001)
    }
}
