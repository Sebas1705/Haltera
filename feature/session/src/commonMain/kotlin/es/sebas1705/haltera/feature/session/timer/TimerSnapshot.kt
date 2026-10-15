package es.sebas1705.ironlog.feature.session.timer

/**
 * The live-session timer as absolute epoch-millisecond instants (spec §5). Nothing here ticks: the UI and
 * the notification render `endsAt − now`, so the timer survives screen-off, background and process death.
 *
 * A timed set runs [countdownEndsAt] (3 s get-ready) → [workEndsAt] → [restEndsAt]; a normal set only has
 * a rest ([restStartsAt]..[restEndsAt]). [sessionId] is null when no session owns the timer.
 */
data class TimerSnapshot(
    val sessionId: String? = null,
    /** Current exercise name, shown by the notification. */
    val label: String = "",
    /** The timed set being performed, so stopping it early can store the real duration. */
    val setId: String? = null,
    val countdownEndsAt: Long? = null,
    val workEndsAt: Long? = null,
    val restStartsAt: Long? = null,
    val restEndsAt: Long? = null,
) {
    /** Future instants at which an alert should fire (end of work, end of rest). */
    fun alertInstants(now: Long): List<Long> = listOfNotNull(workEndsAt, restEndsAt).filter { it > now }.distinct()
}

enum class TimerPhase { IDLE, COUNTDOWN, WORK, REST }

/** Pure timer arithmetic on absolute instants. */
object TimerMath {
    const val COUNTDOWN_MS = 3_000L
    const val MILLIS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3_600L

    fun phase(snapshot: TimerSnapshot, now: Long): TimerPhase = when {
        snapshot.countdownEndsAt != null && now < snapshot.countdownEndsAt -> TimerPhase.COUNTDOWN
        snapshot.workEndsAt != null && now < snapshot.workEndsAt -> TimerPhase.WORK
        snapshot.restEndsAt != null && now < snapshot.restEndsAt -> TimerPhase.REST
        else -> TimerPhase.IDLE
    }

    /** End of the current phase, or null when idle. */
    fun phaseEndsAt(snapshot: TimerSnapshot, now: Long): Long? = when (phase(snapshot, now)) {
        TimerPhase.COUNTDOWN -> snapshot.countdownEndsAt
        TimerPhase.WORK -> snapshot.workEndsAt
        TimerPhase.REST -> snapshot.restEndsAt
        TimerPhase.IDLE -> null
    }

    /** Milliseconds left in the current phase; 0 when idle. */
    fun remainingMillis(snapshot: TimerSnapshot, now: Long): Long =
        phaseEndsAt(snapshot, now)?.let { (it - now).coerceAtLeast(0) } ?: 0

    /** Whole seconds left, rounded up so the display reaches 0 exactly when the phase ends. */
    fun remainingSeconds(snapshot: TimerSnapshot, now: Long): Long =
        (remainingMillis(snapshot, now) + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND

    /** Rest left as a fraction in 0..1 (1 = just started), or 0 outside a rest. */
    fun restFraction(snapshot: TimerSnapshot, now: Long): Float {
        val start = snapshot.restStartsAt ?: snapshot.workEndsAt ?: return 0f
        val end = snapshot.restEndsAt ?: return 0f
        if (phase(snapshot, now) != TimerPhase.REST || end <= start) return 0f
        return ((end - now).toFloat() / (end - start)).coerceIn(0f, 1f)
    }

    /** "m:ss" for a duration in seconds (negative values are shown as 0). */
    fun formatSeconds(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return "${s / SECONDS_PER_MINUTE}:${two(s % SECONDS_PER_MINUTE)}"
    }

    /** "h:mm:ss", or "m:ss" under an hour, for an elapsed duration in milliseconds. */
    fun formatElapsed(millis: Long): String {
        val total = millis.coerceAtLeast(0) / MILLIS_PER_SECOND
        val hours = total / SECONDS_PER_HOUR
        val minutes = (total % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = total % SECONDS_PER_MINUTE
        return if (hours > 0) "$hours:${two(minutes)}:${two(seconds)}" else "$minutes:${two(seconds)}"
    }

    private fun two(value: Long) = value.toString().padStart(2, '0')
}
