package es.sebas1705.haltera.data.repository.export

import es.sebas1705.haltera.data.models.BodyMeasurement
import es.sebas1705.haltera.data.models.BodyWeightEntry
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.MealEntry
import es.sebas1705.haltera.data.models.SetLog
import es.sebas1705.haltera.data.models.WorkoutSession
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The Haltera data export (docs/EXPORT-FORMAT.md): file names, CSV columns and the pure functions that turn
 * the models into rows. Nothing here reads a locale: numbers use a decimal point, dates are ISO 8601.
 */
@OptIn(ExperimentalTime::class)
@Suppress("TooManyFunctions") // One small pure function per column group and number format.
object ExportFormat {
    const val FORMAT = "haltera-export"

    /**
     * 2 (schema 8): `sessions.csv` gains `routine_id`, `routine_name`, `routine_day_index`, `slot_id` and `both_hands`,
     * so importing an export keeps routine sessions linked and dumbbell volume right.
     * 3 (schema 9): adds `body_measurements.csv`. Version 1 and 2 files still import.
     */
    const val VERSION = 3

    const val SESSIONS_FILE = "sessions.csv"
    const val BODY_WEIGHT_FILE = "body_weight.csv"
    const val BODY_MEASUREMENTS_FILE = "body_measurements.csv"
    const val MEAL_ENTRIES_FILE = "meal_entries.csv"
    const val ROUTINES_FILE = "routines.json"
    const val EXERCISES_FILE = "exercises.json"
    const val MANIFEST_FILE = "manifest.json"

    /** Unit of a load that is a weight; stack positions and band levels have their own units. */
    const val UNIT_KG = "kg"
    const val UNIT_PLATE = "plate"
    const val UNIT_LEVEL = "level"

    val SESSION_COLUMNS = listOf(
        "date", "session_id", "started_at", "ended_at", "day_name", "session_notes", "session_body_weight_kg",
        "exercise_id", "variant_id", "exercise", "variant", "exercise_position", "exercise_notes",
        "set_number", "set_type", "reps", "load_grams", "load_kg", "unit", "load_detail",
        "duration_s", "distance_m", "rpe", "completed_at", "set_id",
        // Version 2.
        "routine_id", "routine_name", "routine_day_index", "slot_id", "both_hands",
    )

    val BODY_WEIGHT_COLUMNS = listOf("date", "measured_at", "weight_kg", "weight_grams", "unit", "notes", "id")

    /** Version 3. `value` is in `unit` (cm or percent); `raw_value` is what is stored (mm, tenths of a percent). */
    val BODY_MEASUREMENT_COLUMNS =
        listOf("date", "measured_at", "type", "custom_name", "value", "unit", "raw_value", "notes", "id")

    /** Unit of a length measurement in `body_measurements.csv`, whatever unit the app shows. */
    const val UNIT_CM = "cm"

    /** Unit of the body fat measurement in `body_measurements.csv`. */
    const val UNIT_PERCENT = "percent"

    val MEAL_COLUMNS = listOf(
        "date", "meal", "position", "entry_id", "food_id", "food", "brand", "barcode", "food_source",
        "amount", "amount_unit", "servings", "kcal", "protein_g", "carbs_g", "fat_g", "fiber_g", "sugar_g",
        "salt_g", "created_at",
    )

    /** `haltera-export-YYYY-MM-DD.zip`, the date in [timeZone]. */
    fun fileName(epochMillis: Long, timeZone: TimeZone): String =
        "haltera-export-${localDate(epochMillis, timeZone)}.zip"

    fun localDate(epochMillis: Long, timeZone: TimeZone): String =
        Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone).date.toString()

    /** UTC instant, ISO 8601 (`2026-10-16T18:30:00Z`; fractional seconds only when the millis are not zero). */
    fun instant(epochMillis: Long): String = Instant.fromEpochMilliseconds(epochMillis).toString()

    /** Grams as kilograms with up to three decimals and no trailing zeros: `82500` is `82.5`. Exact. */
    fun kilograms(grams: Long): String {
        val negative = grams < 0
        val magnitude = if (negative) -(grams + 1) + 1 else grams // abs without overflow on Long.MIN_VALUE
        val whole = magnitude / GRAMS_PER_KG
        val fraction = (magnitude % GRAMS_PER_KG).toString().padStart(KG_DECIMALS, '0').trimEnd('0')
        return (if (negative) "-" else "") + whole + if (fraction.isEmpty()) "" else ".$fraction"
    }

    /** [value] with at most [maxDecimals] decimals, a point as separator, no exponent, no trailing zeros. */
    fun decimal(value: Double, maxDecimals: Int): String {
        if (!value.isFinite()) return ""
        val factor = 10.0.pow(maxDecimals)
        val scaled = (abs(value) * factor).roundToLong()
        val unit = factor.toLong()
        val fraction = (scaled % unit).toString().padStart(maxDecimals, '0').trimEnd('0')
        val sign = if (value < 0 && scaled != 0L) "-" else ""
        return sign + (scaled / unit) + if (fraction.isEmpty()) "" else ".$fraction"
    }

    /** The unit a set's load is in: kilograms, a machine stack position (plate) or a band level. */
    fun loadUnit(loadType: LoadType?, loadDetail: String?): String {
        val detail = detailType(loadDetail)
        return when {
            detail == "machinePlate" -> UNIT_PLATE
            detail == "band" || loadType == LoadType.BAND -> UNIT_LEVEL
            else -> UNIT_KG
        }
    }

    private fun detailType(loadDetail: String?): String? {
        if (loadDetail.isNullOrBlank()) return null
        val element = try {
            Json.parseToJsonElement(loadDetail)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return ((element as? JsonObject)?.get("type") as? JsonPrimitive)?.content
    }

    /** What the exporter needs to know about the variant of a performed exercise. */
    data class VariantInfo(
        val variantId: String,
        val exerciseId: String,
        val exercise: String,
        val variant: String,
        val loadType: LoadType?,
    )

    /**
     * One row per completed set of [session]; sessions without completed sets give none. `routine_name` is [routineName]
     * (the routine's current name) or, once the routine is gone, the name the session kept ([WorkoutSession.routineName]).
     */
    fun sessionRows(
        session: WorkoutSession,
        variants: (String) -> VariantInfo,
        timeZone: TimeZone,
        routineName: String? = null,
    ): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val head = listOf(
            localDate(session.startedAt, timeZone),
            session.id,
            instant(session.startedAt),
            session.endedAt?.let(::instant).orEmpty(),
            session.dayName.orEmpty(),
            session.notes,
            session.bodyWeightGrams?.let(::kilograms).orEmpty(),
        )
        session.exercises.sortedBy { it.position }.forEachIndexed { exerciseIndex, exercise ->
            val info = variants(exercise.variantId)
            val exerciseColumns = listOf(
                info.exerciseId,
                info.variantId,
                info.exercise,
                info.variant,
                (exerciseIndex + 1).toString(),
                exercise.notes,
            )
            val link = listOf(
                session.routineId.orEmpty(),
                (routineName ?: session.routineName).orEmpty(),
                session.routineDayIndex?.toString().orEmpty(),
                exercise.slotId.orEmpty(),
                if (exercise.bilateral) "1" else "0",
            )
            exercise.sets.filter { it.completed }.sortedBy { it.position }.forEachIndexed { setIndex, set ->
                rows += head + exerciseColumns + setColumns(set, setIndex + 1, info.loadType) + link
            }
        }
        return rows
    }

    private fun setColumns(set: SetLog, number: Int, loadType: LoadType?): List<String> {
        val unit = loadUnit(loadType, set.loadDetail)
        return listOf(
            number.toString(),
            set.kind.name,
            set.reps?.toString().orEmpty(),
            set.loadGrams?.toString().orEmpty(),
            if (unit == UNIT_KG) set.loadGrams?.let(::kilograms).orEmpty() else "",
            if (set.loadGrams != null) unit else "",
            set.loadDetail.orEmpty(),
            set.durationS?.toString().orEmpty(),
            set.distanceM?.toString().orEmpty(),
            set.rpe?.let { decimal(it, RPE_DECIMALS) }.orEmpty(),
            set.completedAt?.let(::instant).orEmpty(),
            set.id,
        )
    }

    fun bodyWeightRow(entry: BodyWeightEntry, timeZone: TimeZone): List<String> = listOf(
        localDate(entry.measuredAt, timeZone),
        instant(entry.measuredAt),
        kilograms(entry.weightGrams),
        entry.weightGrams.toString(),
        UNIT_KG,
        entry.notes,
        entry.id,
    )

    /**
     * One measurement: lengths in centimetres (millimetres / 10, exact) and body fat in percent (tenths / 10, exact),
     * plus the stored integer. `custom_name` is empty except for custom measurements.
     */
    fun bodyMeasurementRow(measurement: BodyMeasurement, timeZone: TimeZone): List<String> = listOf(
        localDate(measurement.measuredAt, timeZone),
        instant(measurement.measuredAt),
        measurement.type.name,
        measurement.customName,
        tenths(measurement.value),
        if (measurement.type.isLength) UNIT_CM else UNIT_PERCENT,
        measurement.value.toString(),
        measurement.notes,
        measurement.id,
    )

    /** [value] / 10 with at most one decimal: `825` is `82.5`, `800` is `80`. Exact. */
    private fun tenths(value: Long): String {
        val negative = value < 0
        val magnitude = if (negative) -(value + 1) + 1 else value
        val fraction = magnitude % TEN
        return (if (negative) "-" else "") + (magnitude / TEN) + if (fraction == 0L) "" else ".$fraction"
    }

    fun mealRow(entry: MealEntry): List<String> {
        val food = entry.food
        val nutrients = food?.let { entry.nutrients }
        return listOf(
            entry.date.toString(),
            entry.meal.name,
            entry.position.toString(),
            entry.id,
            entry.foodId,
            food?.nameEn.orEmpty(),
            food?.brand.orEmpty(),
            food?.barcode.orEmpty(),
            food?.source?.name.orEmpty(),
            decimal(entry.grams, AMOUNT_DECIMALS),
            food?.unit?.name?.lowercase().orEmpty(),
            entry.servings?.let { decimal(it, AMOUNT_DECIMALS) }.orEmpty(),
            nutrients?.let { decimal(it.kcal, NUTRIENT_DECIMALS) }.orEmpty(),
            nutrients?.let { decimal(it.protein, NUTRIENT_DECIMALS) }.orEmpty(),
            nutrients?.let { decimal(it.carbs, NUTRIENT_DECIMALS) }.orEmpty(),
            nutrients?.let { decimal(it.fat, NUTRIENT_DECIMALS) }.orEmpty(),
            nutrients?.fiber?.let { decimal(it, NUTRIENT_DECIMALS) }.orEmpty(),
            nutrients?.sugar?.let { decimal(it, NUTRIENT_DECIMALS) }.orEmpty(),
            nutrients?.salt?.let { decimal(it, NUTRIENT_DECIMALS) }.orEmpty(),
            instant(entry.createdAt),
        )
    }

    private const val TEN = 10L
    private const val GRAMS_PER_KG = 1000L
    private const val KG_DECIMALS = 3
    private const val RPE_DECIMALS = 2
    private const val AMOUNT_DECIMALS = 2
    private const val NUTRIENT_DECIMALS = 2
}

/** RFC 4180 CSV text: UTF-8 with a byte order mark, CRLF line ends, fields quoted only when they need it. */
internal class CsvWriter(private val out: ByteArrayOutput, columns: List<String>) {
    var rows: Int = 0
        private set

    init {
        out.write(BOM)
        line(columns)
    }

    fun row(values: List<String>) {
        line(values)
        rows++
    }

    private fun line(values: List<String>) {
        val text = StringBuilder()
        values.forEachIndexed { index, value ->
            if (index > 0) text.append(',')
            text.append(escape(value))
        }
        text.append("\r\n")
        out.writeText(text.toString())
    }

    companion object {
        /** The UTF-8 byte order mark, so spreadsheets read accents correctly. Readers strip it. */
        val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

        private val SPECIAL = charArrayOf(',', '"', '\n', '\r')

        fun escape(value: String): String =
            if (value.any { it in SPECIAL }) {
                "\"" + value.replace("\"", "\"\"") + "\""
            } else {
                value
            }
    }
}
