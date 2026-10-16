package es.sebas1705.haltera.data.repository.importer

import es.sebas1705.haltera.data.local.SearchText
import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.SetKind
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Pure readers of the loosely formatted values of third-party exports. Nothing here reads the device locale. */
@Suppress("TooManyFunctions") // One small reader per kind of value.
internal object ImportValues {
    private const val GRAMS_PER_KG = 1000.0
    private const val GRAMS_PER_LB = 453.59237
    private const val METERS_PER_KM = 1000.0
    private const val METERS_PER_MILE = 1609.344
    private const val METERS_PER_FOOT = 0.3048
    private const val METERS_PER_YARD = 0.9144
    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3600
    private const val THOUSANDS_DIGITS = 3
    private const val MAX_PLAUSIBLE_YEAR = 2200
    private const val MIN_PLAUSIBLE_YEAR = 1950
    private const val TWO_DIGIT_YEAR_BASE = 2000
    private const val MONTHS_IN_YEAR = 12
    private const val HOURS_IN_DAY = 24
    private const val MINUTES_IN_HOUR = 60
    private const val PREFIX_LONG = 4
    private const val PREFIX_SHORT = 3
    private const val MAX_RPE = 10.0
    private const val TIME_GROUP = 4
    private const val MAX_CLOCK_PARTS = 3

    // region numbers

    /**
     * A number with a decimal point or a decimal comma, with or without thousands separators
     * (`1,234.5`, `1.234,5`, `82,5`, `82.5`). A lone comma followed by exactly three digits after a non-zero
     * integer part (`1,250`) is a thousands separator. Null when it is not a finite number.
     */
    fun decimal(raw: String?): Double? {
        val s = raw?.trim()?.replace(" ", "")?.replace(" ", "") ?: return null
        if (s.isEmpty()) return null
        val comma = s.lastIndexOf(',')
        val point = s.lastIndexOf('.')
        val normalised = when {
            comma >= 0 && point >= 0 ->
                if (comma > point) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            comma >= 0 -> if (commaIsThousands(s, comma)) s.replace(",", "") else s.replace(',', '.')
            s.count { it == '.' } > 1 -> s.replace(".", "")
            else -> s
        }
        return normalised.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    private fun commaIsThousands(s: String, comma: Int): Boolean {
        val single = s.indexOf(',') == comma
        if (!single) return true
        val after = s.length - comma - 1
        val intPart = s.substring(0, comma)
        return after == THOUSANDS_DIGITS && intPart.isNotEmpty() && !intPart.startsWith("0")
    }

    fun integer(raw: String?): Int? = decimal(raw)?.takeIf { it >= 0 && it <= Int.MAX_VALUE }?.roundToInt()

    fun rpe(raw: String?): Double? = decimal(raw)?.takeIf { it > 0 && it <= MAX_RPE }

    fun grams(weight: Double, unit: ImportWeightUnit): Long = when (unit) {
        ImportWeightUnit.KG -> (weight * GRAMS_PER_KG).roundToLong()
        ImportWeightUnit.LB -> (weight * GRAMS_PER_LB).roundToLong()
    }

    fun weightUnit(raw: String?): ImportWeightUnit? {
        val s = raw?.trim()?.lowercase() ?: return null
        return when {
            s.startsWith("kg") || s.startsWith("kilo") -> ImportWeightUnit.KG
            s.startsWith("lb") || s.startsWith("pound") -> ImportWeightUnit.LB
            else -> null
        }
    }

    /** Whole metres from [value] in [unit] (`km`, `mi`, `m`, `ft`, `yd`; metres when blank or unknown). */
    fun meters(value: Double, unit: String?): Int {
        val factor = when (unit?.trim()?.lowercase()) {
            "km", "kms", "kilometer", "kilometers", "kilometre", "kilometres" -> METERS_PER_KM
            "mi", "mile", "miles" -> METERS_PER_MILE
            "ft", "foot", "feet" -> METERS_PER_FOOT
            "yd", "yard", "yards" -> METERS_PER_YARD
            else -> 1.0
        }
        return (value * factor).roundToInt()
    }

    // endregion

    // region durations

    private val durationUnits = Regex("""(\d+(?:[.,]\d+)?)\s*(h|min|m|sec|s)\b""")

    /** Seconds from `1h 5m 3s`, `52m`, `200s`, `1:05:00`, `05:30` (minutes:seconds) or a plain number of seconds. */
    fun durationSeconds(raw: String?): Int? {
        val s = raw?.trim()?.lowercase()
        return when {
            s.isNullOrEmpty() -> null
            ':' in s -> clockSeconds(s)
            else -> {
                val units = durationUnits.findAll(s).toList()
                if (units.isEmpty()) decimal(s)?.takeIf { it >= 0 }?.roundToInt() else unitSeconds(units)
            }
        }
    }

    private fun clockSeconds(s: String): Int? {
        val parts = s.split(':').map { it.trim().toDoubleOrNull() ?: return null }
        if (parts.size !in 2..MAX_CLOCK_PARTS) return null
        // m:ss or h:mm:ss, folded from the left.
        return parts.fold(0.0) { total, part -> total * SECONDS_PER_MINUTE + part }.roundToInt()
    }

    private fun unitSeconds(units: List<MatchResult>): Int? {
        var total = 0.0
        for (m in units) {
            val n = decimal(m.groupValues[1]) ?: return null
            total += when (m.groupValues[2]) {
                "h" -> n * SECONDS_PER_HOUR
                "m", "min" -> n * SECONDS_PER_MINUTE
                else -> n
            }
        }
        return total.roundToInt()
    }

    // endregion

    // region dates

    /** A calendar date and, when the file has one, a time of day. */
    data class When(val date: LocalDate, val hour: Int?, val minute: Int, val second: Int) {
        fun toLocalDateTime(defaultHour: Int = 0): LocalDateTime =
            LocalDateTime(date.year, date.month.ordinal + 1, date.day, hour ?: defaultHour, minute, second)
    }

    private val isoDate = Regex("""^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})(?:[T ,]+(\d{1,2}):(\d{2})(?::(\d{2}))?)?""")
    private val dayMonthName =
        Regex("""^(\d{1,2})\s+(\p{L}{3,})\.?,?\s+(\d{4})(?:,?\s+(\d{1,2}):(\d{2})(?::(\d{2}))?)?""")
    private val monthNameDay =
        Regex("""^(\p{L}{3,})\.?\s+(\d{1,2}),?\s+(\d{4})(?:,?\s+(\d{1,2}):(\d{2})(?::(\d{2}))?)?""")
    private val numericDate =
        Regex("""^(\d{1,2})[/.-](\d{1,2})[/.-](\d{4}|\d{2})(?:[,T ]+(\d{1,2}):(\d{2})(?::(\d{2}))?)?""")

    // Month names in the languages the exporters localise to; keys are lower case without accents.
    private val months: Map<String, Int> = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8,
        "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
        "janv" to 1, "fevr" to 2, "mars" to 3, "avr" to 4, "mai" to 5, "juin" to 6, "juil" to 7, "aout" to 8,
        "sept" to 9,
        "ene" to 1, "abr" to 4, "ago" to 8, "dic" to 12, "set" to 9, "out" to 10, "fev" to 2,
        "mrz" to 3, "okt" to 10, "dez" to 12, "mei" to 5, "mrt" to 3, "maart" to 3,
        "gen" to 1, "mag" to 5, "giu" to 6, "lug" to 7, "ott" to 10,
    )

    private fun monthNumber(word: String): Int? {
        val key = SearchText.normalize(word)
        return months[key] ?: months[key.take(PREFIX_LONG)] ?: months[key.take(PREFIX_SHORT)]
    }

    /**
     * A date, with its time when present: `2025-04-29 12:00:00`, `2025-04-29T12:00`, `29 Apr 2025, 12:00` (also
     * in Spanish, French, German, Portuguese, Italian and Dutch), `Apr 29, 2025`, and `29/04/2025` (day first
     * unless the second number cannot be a month). Null when it is none of these or not a real date.
     */
    @Suppress("MagicNumber") // Regex group numbers.
    fun whenOf(raw: String?): When? {
        val s = raw?.trim() ?: return null
        return isoDate.find(s)?.let { m -> build(m.groupValues[1], m.groupValues[2], m.groupValues[3], m) }
            ?: dayMonthName.find(s)?.let { m -> named(year = m.groupValues[3], month = m.groupValues[2], day = m.groupValues[1], m) }
            ?: monthNameDay.find(s)?.let { m -> named(year = m.groupValues[3], month = m.groupValues[1], day = m.groupValues[2], m) }
            ?: numericDate.find(s)?.let(::numeric)
    }

    private fun named(year: String, month: String, day: String, m: MatchResult): When? =
        monthNumber(month)?.let { build(year, it.toString(), day, m) }

    @Suppress("MagicNumber") // Regex group numbers.
    private fun numeric(m: MatchResult): When? {
        val a = m.groupValues[1].toInt()
        val b = m.groupValues[2].toInt()
        val (day, month) = if (b > MONTHS_IN_YEAR && a <= MONTHS_IN_YEAR) b to a else a to b
        val year = m.groupValues[3].let { y -> if (y.length == 2) (TWO_DIGIT_YEAR_BASE + y.toInt()).toString() else y }
        return build(year, month.toString(), day.toString(), m)
    }

    private fun build(year: String, month: String, day: String, m: MatchResult): When? {
        val y = year.toInt()
        val mo = month.toInt()
        val d = day.toInt()
        if (y !in MIN_PLAUSIBLE_YEAR..MAX_PLAUSIBLE_YEAR || mo !in 1..MONTHS_IN_YEAR) return null
        val date = try {
            LocalDate(y, mo, d)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val hour = m.groupValues[TIME_GROUP].toIntOrNull()
        val minute = m.groupValues[TIME_GROUP + 1].toIntOrNull() ?: 0
        val second = m.groupValues[TIME_GROUP + 2].toIntOrNull() ?: 0
        val invalidTime = hour != null && (hour >= HOURS_IN_DAY || minute >= MINUTES_IN_HOUR || second >= MINUTES_IN_HOUR)
        return if (invalidTime) null else When(date, hour, minute, second)
    }

    // endregion

    // region set types and equipment

    /** Warm-up, drop and failure markers of every source (`warmup`, `dropset`, `failure`, `W`, `D`, `F`...). */
    fun setKind(raw: String?): SetKind {
        val s = raw?.trim()?.lowercase().orEmpty()
        return when {
            s.isEmpty() -> SetKind.WORKING
            s == "w" || s.contains("warm") -> SetKind.WARMUP
            s == "d" || s.contains("drop") -> SetKind.DROP
            s == "f" || s.contains("fail") -> SetKind.FAILURE
            else -> SetKind.WORKING
        }
    }

    private val equipmentWords: Map<String, Equipment> = mapOf(
        "barbell" to Equipment.BARBELL, "bb" to Equipment.BARBELL, "barra" to Equipment.BARBELL,
        "ez bar" to Equipment.EZ_BAR, "ez barbell" to Equipment.EZ_BAR, "ez" to Equipment.EZ_BAR,
        "trap bar" to Equipment.TRAP_BAR, "hex bar" to Equipment.TRAP_BAR,
        "dumbbell" to Equipment.DUMBBELL, "dumbbells" to Equipment.DUMBBELL, "db" to Equipment.DUMBBELL,
        "mancuerna" to Equipment.DUMBBELL, "mancuernas" to Equipment.DUMBBELL,
        "kettlebell" to Equipment.KETTLEBELL, "kb" to Equipment.KETTLEBELL,
        "smith machine" to Equipment.SMITH, "smith" to Equipment.SMITH, "multipower" to Equipment.SMITH,
        "machine" to Equipment.MACHINE, "plate loaded" to Equipment.MACHINE, "lever" to Equipment.MACHINE,
        "maquina" to Equipment.MACHINE, "machine assisted" to Equipment.MACHINE,
        "cable" to Equipment.CABLE, "pulley" to Equipment.CABLE, "polea" to Equipment.CABLE,
        "band" to Equipment.BAND, "resistance band" to Equipment.BAND, "banda" to Equipment.BAND,
        "bodyweight" to Equipment.BODYWEIGHT, "body weight" to Equipment.BODYWEIGHT,
    )

    /** The equipment a word or phrase names (`Barbell`, `smith machine`), or null. */
    fun equipmentOf(phrase: String?): Equipment? = phrase?.let { equipmentWords[matchKey(it)] }

    /** Lower case, no accents, every non-alphanumeric run reduced to one space: `Pull-Up` and `pull up` agree. */
    fun matchKey(text: String): String {
        val out = StringBuilder()
        var gap = true
        for (c in SearchText.normalize(text)) {
            if (c.isLetterOrDigit()) {
                out.append(c)
                gap = false
            } else if (!gap) {
                out.append(' ')
                gap = true
            }
        }
        return out.toString().trimEnd()
    }

    // endregion

    // region ids

    private const val FNV_OFFSET_A = -3750763034362895579L // 0xCBF29CE484222325
    private const val FNV_OFFSET_B = 0x1B873593AL
    private const val FNV_PRIME = 1099511628211L
    private const val HEX_RADIX = 16
    private const val HEX_LENGTH = 16
    private const val BYTE_MASK = 0xFF

    private fun fnv(text: String, offset: Long): Long {
        var h = offset
        for (b in text.encodeToByteArray()) {
            h = (h xor (b.toLong() and BYTE_MASK.toLong())) * FNV_PRIME
        }
        return h
    }

    /**
     * A deterministic UUID-shaped id from [parts]: the same input always gives the same id, on every platform.
     * Two independent 64-bit FNV-1a hashes make accidental collisions between sessions practically impossible.
     */
    @Suppress("MagicNumber") // The slices of the UUID text form.
    fun stableId(vararg parts: String): String {
        val key = parts.joinToString("\u001F")
        val hex = hex64(fnv(key, FNV_OFFSET_A)) + hex64(fnv("$key\u001E", FNV_OFFSET_B))
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
            "${hex.substring(16, 20)}-${hex.substring(20, 32)}"
    }

    private fun hex64(value: Long): String = value.toULong().toString(HEX_RADIX).padStart(HEX_LENGTH, '0')

    // endregion
}
