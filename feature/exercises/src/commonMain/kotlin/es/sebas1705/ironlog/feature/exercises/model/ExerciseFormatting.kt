package es.sebas1705.ironlog.feature.exercises.model

import es.sebas1705.ironlog.data.models.Equipment
import es.sebas1705.ironlog.data.models.LoadType
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Base URL of the free-exercise-db frames, pinned to a commit so paths never move (spec §6.4). */
internal const val FRAMES_BASE_URL =
    "https://raw.githubusercontent.com/yuhonas/free-exercise-db/f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5/exercises/"

/** Full URL of a frame path relative to free-exercise-db's `exercises` folder. */
internal fun frameUrl(path: String): String = FRAMES_BASE_URL + path.trimStart('/')

/**
 * The load type a new variant gets for [this] equipment: the first allowed one in the catalogue
 * pipeline (`scripts/catalog/lib/mapping.mjs`, `ALLOWED_LOAD_TYPES`).
 */
internal fun Equipment.defaultLoadType(): LoadType = when (this) {
    Equipment.BARBELL, Equipment.EZ_BAR, Equipment.TRAP_BAR, Equipment.SMITH -> LoadType.BARBELL
    Equipment.DUMBBELL, Equipment.KETTLEBELL, Equipment.MEDICINE_BALL -> LoadType.DUMBBELL
    Equipment.MACHINE, Equipment.CABLE -> LoadType.MACHINE
    Equipment.BAND -> LoadType.BAND
    Equipment.BODYWEIGHT -> LoadType.BODYWEIGHT
    Equipment.SWISS_BALL, Equipment.FOAM_ROLLER, Equipment.CARDIO_MACHINE, Equipment.OTHER, Equipment.NONE -> LoadType.NONE
}

/** Local calendar date of [epochMillis] as ISO `yyyy-MM-dd` (locale independent). */
internal fun formatDate(epochMillis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): String =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone).date.toString()

/** A duration in seconds as `m:ss`, or `h:mm:ss` from one hour. */
internal fun formatDuration(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    val hours = total / SECONDS_PER_HOUR
    val minutes = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val secs = total % SECONDS_PER_MINUTE
    val ss = secs.toString().padStart(2, '0')
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$ss" else "$minutes:$ss"
}

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3_600
