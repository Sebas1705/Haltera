package es.sebas1705.ironlog.feature.progress.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.intl.Locale
import es.sebas1705.ironlog.core.units.WeightUnit
import es.sebas1705.ironlog.core.units.Weights
import es.sebas1705.ironlog.data.models.Equipment
import es.sebas1705.ironlog.data.models.Muscle
import es.sebas1705.ironlog.feature.progress.model.ChartScale
import es.sebas1705.ironlog.feature.progress.model.VariantInfo
import es.sebas1705.ironlog.feature.progress.model.toLocalDate
import es.sebas1705.ironlog.feature.progress.resources.Res
import es.sebas1705.ironlog.feature.progress.resources.progress_date_full
import es.sebas1705.ironlog.feature.progress.resources.progress_date_short
import es.sebas1705.ironlog.feature.progress.resources.progress_duration_hm
import es.sebas1705.ironlog.feature.progress.resources.progress_duration_m
import es.sebas1705.ironlog.feature.progress.resources.progress_equipment_band
import es.sebas1705.ironlog.feature.progress.resources.progress_equipment_barbell
import es.sebas1705.ironlog.feature.progress.resources.progress_equipment_bodyweight
import es.sebas1705.ironlog.feature.progress.resources.progress_equipment_cable
import es.sebas1705.ironlog.feature.progress.resources.progress_equipment_cardio_machine
import es.sebas1705.ironlog.feature.progress.resources.progress_equipment_dumbbell
import es.sebas1705.ironlog.feature.progress.resources.progress_equipment_ez_bar
import es.sebas1705.ironlog.feature.progress.resources.progress_equipment_foam_roller
import es.sebas1705.ironlog.feature.progress.resources.progress_equipment_kettlebell
import es.sebas1705.ironlog.feature.progress.resources.progress_equipment_machine
import es.sebas1705.ironlog.feature.progress.resources.progress_equipment_medicine_ball
import es.sebas1705.ironlog.feature.progress.resources.progress_equipment_none
import es.sebas1705.ironlog.feature.progress.resources.progress_equipment_other
import es.sebas1705.ironlog.feature.progress.resources.progress_equipment_smith
import es.sebas1705.ironlog.feature.progress.resources.progress_equipment_swiss_ball
import es.sebas1705.ironlog.feature.progress.resources.progress_equipment_trap_bar
import es.sebas1705.ironlog.feature.progress.resources.progress_months_short
import es.sebas1705.ironlog.feature.progress.resources.progress_muscle_abdominals
import es.sebas1705.ironlog.feature.progress.resources.progress_muscle_abductors
import es.sebas1705.ironlog.feature.progress.resources.progress_muscle_adductors
import es.sebas1705.ironlog.feature.progress.resources.progress_muscle_biceps
import es.sebas1705.ironlog.feature.progress.resources.progress_muscle_calves
import es.sebas1705.ironlog.feature.progress.resources.progress_muscle_chest
import es.sebas1705.ironlog.feature.progress.resources.progress_muscle_forearms
import es.sebas1705.ironlog.feature.progress.resources.progress_muscle_full_body
import es.sebas1705.ironlog.feature.progress.resources.progress_muscle_glutes
import es.sebas1705.ironlog.feature.progress.resources.progress_muscle_hamstrings
import es.sebas1705.ironlog.feature.progress.resources.progress_muscle_lats
import es.sebas1705.ironlog.feature.progress.resources.progress_muscle_lower_back
import es.sebas1705.ironlog.feature.progress.resources.progress_muscle_middle_back
import es.sebas1705.ironlog.feature.progress.resources.progress_muscle_neck
import es.sebas1705.ironlog.feature.progress.resources.progress_muscle_quadriceps
import es.sebas1705.ironlog.feature.progress.resources.progress_muscle_shoulders
import es.sebas1705.ironlog.feature.progress.resources.progress_muscle_traps
import es.sebas1705.ironlog.feature.progress.resources.progress_muscle_triceps
import es.sebas1705.ironlog.feature.progress.resources.progress_unit_kg
import es.sebas1705.ironlog.feature.progress.resources.progress_unit_lb
import es.sebas1705.ironlog.feature.progress.resources.progress_unknown_exercise
import es.sebas1705.ironlog.feature.progress.resources.progress_variant_name
import es.sebas1705.ironlog.feature.progress.resources.progress_weight_value
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.pow
import kotlin.math.roundToLong

/** The UI language code ("en", "es"), used to pick catalogue names. */
@Composable
internal fun currentLanguage(): String = Locale.current.language

@Composable
internal fun unitLabel(unit: WeightUnit): String = stringResource(
    when (unit) {
        WeightUnit.KG -> Res.string.progress_unit_kg
        WeightUnit.LB -> Res.string.progress_unit_lb
    },
)

/** "62.5 kg": [grams] in [unit] with the unit label. */
@Composable
internal fun formatWeight(grams: Long, unit: WeightUnit, decimals: Int = 2): String =
    stringResource(Res.string.progress_weight_value, Weights.format(grams, unit, decimals), unitLabel(unit))

/** Volume (gram-reps) shown like a weight with no decimals: "4250 kg". */
@Composable
internal fun formatVolume(gramReps: Long, unit: WeightUnit): String = formatWeight(gramReps, unit, decimals = 0)

/** "Oct 14" / "14 oct", or with the year. */
@Composable
internal fun formatDate(date: LocalDate, withYear: Boolean = false): String {
    val month = stringArrayResource(Res.array.progress_months_short)[date.month.number - 1]
    return if (withYear) {
        stringResource(Res.string.progress_date_full, date.day.toString(), month, date.year.toString())
    } else {
        stringResource(Res.string.progress_date_short, date.day.toString(), month)
    }
}

@Composable
internal fun formatDate(millis: Long, withYear: Boolean = false): String =
    formatDate(millis.toLocalDate(TimeZone.currentSystemDefault()), withYear)

/** "1 h 05 min" or "45 min". */
@Composable
internal fun formatDuration(millis: Long): String {
    val totalMinutes = millis / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        stringResource(Res.string.progress_duration_hm, hours.toString(), minutes.toString().padStart(2, '0'))
    } else {
        stringResource(Res.string.progress_duration_m, minutes.toString())
    }
}

/** "<exercise> (<variant label>)": the variant's own name, else its equipment (ExerciseNaming). */
@Composable
internal fun variantLabel(info: VariantInfo?): String {
    if (info == null) return stringResource(Res.string.progress_unknown_exercise)
    val language = currentLanguage()
    val label = info.variantName?.get(language) ?: equipmentLabel(info.equipment)
    return stringResource(Res.string.progress_variant_name, info.exerciseName.get(language), label)
}

@Composable
internal fun muscleLabel(muscle: Muscle): String = stringResource(muscleResource(muscle))

@Composable
internal fun equipmentLabel(equipment: Equipment): String = stringResource(equipmentResource(equipment))

private fun muscleResource(muscle: Muscle): StringResource = when (muscle) {
    Muscle.ABDOMINALS -> Res.string.progress_muscle_abdominals
    Muscle.ABDUCTORS -> Res.string.progress_muscle_abductors
    Muscle.ADDUCTORS -> Res.string.progress_muscle_adductors
    Muscle.BICEPS -> Res.string.progress_muscle_biceps
    Muscle.CALVES -> Res.string.progress_muscle_calves
    Muscle.CHEST -> Res.string.progress_muscle_chest
    Muscle.FOREARMS -> Res.string.progress_muscle_forearms
    Muscle.GLUTES -> Res.string.progress_muscle_glutes
    Muscle.HAMSTRINGS -> Res.string.progress_muscle_hamstrings
    Muscle.LATS -> Res.string.progress_muscle_lats
    Muscle.LOWER_BACK -> Res.string.progress_muscle_lower_back
    Muscle.MIDDLE_BACK -> Res.string.progress_muscle_middle_back
    Muscle.NECK -> Res.string.progress_muscle_neck
    Muscle.QUADRICEPS -> Res.string.progress_muscle_quadriceps
    Muscle.SHOULDERS -> Res.string.progress_muscle_shoulders
    Muscle.TRAPS -> Res.string.progress_muscle_traps
    Muscle.TRICEPS -> Res.string.progress_muscle_triceps
    Muscle.FULL_BODY -> Res.string.progress_muscle_full_body
}

private fun equipmentResource(equipment: Equipment): StringResource = when (equipment) {
    Equipment.BARBELL -> Res.string.progress_equipment_barbell
    Equipment.EZ_BAR -> Res.string.progress_equipment_ez_bar
    Equipment.TRAP_BAR -> Res.string.progress_equipment_trap_bar
    Equipment.DUMBBELL -> Res.string.progress_equipment_dumbbell
    Equipment.KETTLEBELL -> Res.string.progress_equipment_kettlebell
    Equipment.SMITH -> Res.string.progress_equipment_smith
    Equipment.MACHINE -> Res.string.progress_equipment_machine
    Equipment.CABLE -> Res.string.progress_equipment_cable
    Equipment.BAND -> Res.string.progress_equipment_band
    Equipment.BODYWEIGHT -> Res.string.progress_equipment_bodyweight
    Equipment.MEDICINE_BALL -> Res.string.progress_equipment_medicine_ball
    Equipment.SWISS_BALL -> Res.string.progress_equipment_swiss_ball
    Equipment.FOAM_ROLLER -> Res.string.progress_equipment_foam_roller
    Equipment.CARDIO_MACHINE -> Res.string.progress_equipment_cardio_machine
    Equipment.OTHER -> Res.string.progress_equipment_other
    Equipment.NONE -> Res.string.progress_equipment_none
}

private const val GROUP_SIZE = 3
private const val GROUP_THRESHOLD = 10_000.0

/**
 * [value] with exactly [decimals] decimals ("." separator); integer parts from 10 000 are grouped by
 * thousands with a narrow no-break space ("12 400"), which reads the same in English and Spanish.
 */
internal fun formatNumber(value: Double, decimals: Int): String {
    val factor = 10.0.pow(decimals)
    val scaled = kotlin.math.abs(value * factor).roundToLong()
    val integer = (scaled / factor.toLong()).toString()
    val fraction = if (decimals > 0) "." + (scaled % factor.toLong()).toString().padStart(decimals, '0') else ""
    val grouped = if (kotlin.math.abs(value) >= GROUP_THRESHOLD) {
        integer.reversed().chunked(GROUP_SIZE).joinToString(" ").reversed()
    } else {
        integer
    }
    val negative = value < 0 && scaled != 0L
    return (if (negative) "−" else "") + grouped + fraction
}

/** Replaces `%1$s`, `%2$d`… in a raw resource pattern (for formatting outside composition). */
internal fun fillPattern(pattern: String, vararg args: String): String =
    args.foldIndexed(pattern) { i, text, arg -> text.replace("%${i + 1}\$s", arg).replace("%${i + 1}\$d", arg) }

/** Weight, volume and axis formatters for one unit, usable from Canvas code. */
internal class UnitFormat(val unit: WeightUnit, val label: String) {
    /** Grams (or gram-reps) in [unit]. */
    fun toUnit(grams: Double): Double = when (unit) {
        WeightUnit.KG -> grams / Weights.GRAMS_PER_KG
        WeightUnit.LB -> grams / Weights.GRAMS_PER_LB
    }

    fun weight(grams: Double, decimals: Int = 1): String = "${trimmed(toUnit(grams), decimals)} $label"

    fun volume(gramReps: Double): String = "${formatNumber(toUnit(gramReps), 0)} $label"

    /** Axis ticks of values already in [unit]. */
    fun axis(value: Double): String = ChartScale.compact(value)

    /** "62.5" / "62": up to [decimals] decimals without trailing zeros. */
    private fun trimmed(value: Double, decimals: Int): String = ChartScale.trim(value, decimals)
}

@Composable
internal fun rememberUnitFormat(unit: WeightUnit): UnitFormat {
    val label = unitLabel(unit)
    return remember(unit, label) { UnitFormat(unit, label) }
}

/** Date and duration formatters usable outside composition (Canvas tooltips and labels). */
internal class DateFormat(
    private val months: List<String>,
    private val shortPattern: String,
    private val fullPattern: String,
    private val hoursPattern: String,
    private val minutesPattern: String,
    private val timeZone: TimeZone,
) {
    fun short(date: LocalDate): String = fillPattern(shortPattern, date.day.toString(), months[date.month.number - 1])

    fun full(date: LocalDate): String =
        fillPattern(fullPattern, date.day.toString(), months[date.month.number - 1], date.year.toString())

    fun short(millis: Long): String = short(millis.toLocalDate(timeZone))

    fun full(millis: Long): String = full(millis.toLocalDate(timeZone))

    fun epochDay(day: Int): String = short(LocalDate.fromEpochDays(day.toLong()))

    fun epochDayFull(day: Int): String = full(LocalDate.fromEpochDays(day.toLong()))

    /** "Oct 5 – Oct 11". */
    fun range(first: Int, last: Int): String = if (first == last) {
        epochDay(
            first
        )
    } else {
        "${epochDay(first)} – ${epochDay(last)}"
    }

    fun duration(millis: Double): String {
        val totalMinutes = (millis / MINUTE_MS).roundToLong()
        val hours = totalMinutes / MINUTES_PER_HOUR
        val minutes = totalMinutes % MINUTES_PER_HOUR
        return if (hours > 0) {
            fillPattern(hoursPattern, hours.toString(), minutes.toString().padStart(2, '0'))
        } else {
            fillPattern(minutesPattern, minutes.toString())
        }
    }

    private companion object {
        const val MINUTE_MS = 60_000.0
        const val MINUTES_PER_HOUR = 60
    }
}

@Composable
internal fun rememberDateFormat(): DateFormat {
    val months = stringArrayResource(Res.array.progress_months_short)
    val short = stringResource(Res.string.progress_date_short)
    val full = stringResource(Res.string.progress_date_full)
    val hours = stringResource(Res.string.progress_duration_hm)
    val minutes = stringResource(Res.string.progress_duration_m)
    return remember(months, short, full, hours, minutes) {
        DateFormat(months, short, full, hours, minutes, TimeZone.currentSystemDefault())
    }
}
