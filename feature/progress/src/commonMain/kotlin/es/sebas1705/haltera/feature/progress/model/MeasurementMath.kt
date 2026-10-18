package es.sebas1705.haltera.feature.progress.model

import es.sebas1705.haltera.core.units.BodyFat
import es.sebas1705.haltera.core.units.LengthUnit
import es.sebas1705.haltera.core.units.Lengths
import es.sebas1705.haltera.core.units.appLanguage
import es.sebas1705.haltera.data.models.BodyMeasurement
import es.sebas1705.haltera.data.models.BodyMeasurements
import es.sebas1705.haltera.data.models.MeasurementType

/**
 * The measurements of one chart: one fixed type, or one custom name. [entries] are latest first; [points] are oldest
 * first with the stored value (millimetres or tenths of a percent) as y.
 */
internal data class MeasurementSeries(
    val key: String,
    val type: MeasurementType,
    /** The custom name as last written (empty for the fixed types). */
    val customName: String,
    val entries: List<BodyMeasurement>,
    val points: List<ChartPoint>,
    /** Latest minus the oldest entry within the 30 days before it, in the stored unit; null without one. */
    val change30Days: Long?,
) {
    val latest: BodyMeasurement get() = entries.first()
}

/** Pure calculations behind the measurements screen. */
internal object MeasurementMath {
    private const val MILLIS_PER_DAY = 86_400_000L

    /**
     * One series per type or custom name that has entries: the fixed types in their usual order, then the custom ones
     * by name. Entries of the same instant keep a stable order (by id).
     */
    fun series(all: List<BodyMeasurement>): List<MeasurementSeries> =
        all.groupBy { it.seriesKey }.map { (key, entries) ->
            val latestFirst = entries.sortedWith(
                compareByDescending<BodyMeasurement> { it.measuredAt }.thenBy { it.id }
            )
            val oldestFirst = latestFirst.reversed()
            MeasurementSeries(
                key = key,
                type = latestFirst.first().type,
                customName = latestFirst.first().customName,
                entries = latestFirst,
                points = oldestFirst.map { ChartPoint(it.measuredAt, it.value.toDouble()) },
                change30Days = change(latestFirst),
            )
        }.sortedWith(
            compareBy<MeasurementSeries> { it.type.ordinal }.thenBy { BodyMeasurements.nameKey(it.customName) }
        )

    /** Latest entry minus the oldest entry measured within [days] days before it; null without an earlier one. */
    fun change(entries: List<BodyMeasurement>, days: Int = ProgressMath.TREND_DAYS): Long? {
        val latest = entries.maxWithOrNull(compareBy<BodyMeasurement> { it.measuredAt }.thenByDescending { it.id })
            ?: return null
        val windowStart = latest.measuredAt - days * MILLIS_PER_DAY
        val baseline = entries
            .filter { it.id != latest.id && it.measuredAt in windowStart..latest.measuredAt }
            .minWithOrNull(compareBy<BodyMeasurement> { it.measuredAt }.thenByDescending { it.id })
            ?: return null
        return latest.value - baseline.value
    }

    /** The custom names already used, one per series (latest spelling), alphabetically: suggestions in the editor. */
    fun customNames(all: List<BodyMeasurement>): List<String> =
        series(all).filter { it.type == MeasurementType.CUSTOM }.map { it.customName }

    /** A typed value in the shown unit to the stored integer, or null when it is not a valid value for [type]. */
    fun parseValue(text: String, type: MeasurementType, unit: LengthUnit): Long? {
        val value = if (type.isLength) Lengths.parse(text, unit) else BodyFat.parse(text)
        return value?.takeIf { BodyMeasurements.isValidValue(type, it) }
    }

    /** The stored value as text for the input field (no grouping), so saving it unchanged keeps the value. */
    fun inputText(value: Long, type: MeasurementType, unit: LengthUnit, language: String = appLanguage()): String =
        if (type.isLength) Lengths.format(value, unit, language, grouping = false) else BodyFat.format(value, language)

    /** The stored value in the shown unit (cm, in or percent), for charts. */
    fun toShown(value: Double, type: MeasurementType, unit: LengthUnit): Double = when {
        !type.isLength -> value / BodyFat.TENTHS_PER_PERCENT
        unit == LengthUnit.CM -> value / Lengths.MM_PER_CM
        else -> value / Lengths.MM_PER_IN
    }
}
