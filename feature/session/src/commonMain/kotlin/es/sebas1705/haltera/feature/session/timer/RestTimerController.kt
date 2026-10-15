package es.sebas1705.haltera.feature.session.timer

import es.sebas1705.haltera.core.datastore.SettingsRepository
import es.sebas1705.haltera.feature.session.platform.RestAlert
import kotlinx.coroutines.flow.StateFlow

/**
 * The single writer of the session timer: the session screen and the Android notification actions both
 * go through it. Every change persists the absolute instants and reschedules the platform alerts.
 */
class RestTimerController(
    private val store: TimerStore,
    private val alert: RestAlert,
    private val settings: SettingsRepository,
    private val now: () -> Long,
) {
    val state: StateFlow<TimerSnapshot> get() = store.state

    /** Starts a rest of [seconds] now (0 or less clears the timer). */
    fun startRest(sessionId: String, label: String, seconds: Int) {
        if (seconds <= 0) return clearPhases(sessionId, label)
        val at = now()
        write(
            TimerSnapshot(
                sessionId = sessionId,
                label = label,
                restStartsAt = at,
                restEndsAt = at + seconds * TimerMath.MILLIS_PER_SECOND,
            ),
        )
    }

    /** Timed set: 3 s countdown, [workSeconds] of work, then [restSeconds] of rest, all fixed now. */
    fun startTimed(sessionId: String, label: String, setId: String, workSeconds: Int, restSeconds: Int) {
        val countdownEnd = now() + TimerMath.COUNTDOWN_MS
        val workEnd = countdownEnd + workSeconds.coerceAtLeast(1) * TimerMath.MILLIS_PER_SECOND
        write(
            TimerSnapshot(
                sessionId = sessionId,
                label = label,
                setId = setId,
                countdownEndsAt = countdownEnd,
                workEndsAt = workEnd,
                restStartsAt = workEnd,
                restEndsAt = workEnd + restSeconds.coerceAtLeast(0) * TimerMath.MILLIS_PER_SECOND,
            ),
        )
    }

    /**
     * Ends the work of a timed set now and starts its rest. Returns the seconds actually worked, or null
     * when no timed set is working.
     */
    fun finishWorkNow(): Int? {
        val s = state.value
        val workEnd = s.workEndsAt ?: return null
        val at = now()
        if (at >= workEnd) return null
        val countdownEnd = s.countdownEndsAt ?: at
        val restMs = (s.restEndsAt ?: workEnd) - workEnd
        write(s.copy(countdownEndsAt = null, workEndsAt = null, restStartsAt = at, restEndsAt = at + restMs))
        return ((at - countdownEnd).coerceAtLeast(0) / TimerMath.MILLIS_PER_SECOND).toInt()
    }

    /** Moves the end of the rest by [deltaSeconds]; it never ends before now or before the work ends. */
    fun adjustRest(deltaSeconds: Int) {
        val s = state.value
        val end = s.restEndsAt ?: return
        val at = now()
        if (end <= at) return
        val floor = maxOf(at, s.workEndsAt ?: at)
        val newEnd = (end + deltaSeconds * TimerMath.MILLIS_PER_SECOND).coerceAtLeast(floor)
        if (newEnd <= at) clearPhases(s.sessionId, s.label) else write(s.copy(restEndsAt = newEnd))
    }

    /** Skips whatever is running (countdown, work or rest). */
    fun skip() {
        val s = state.value
        clearPhases(s.sessionId, s.label)
    }

    /** Updates the exercise shown by the notification without touching the timer. */
    fun setLabel(sessionId: String, label: String) {
        val s = state.value
        when {
            s.sessionId == sessionId && s.label == label -> Unit
            s.sessionId != sessionId -> write(TimerSnapshot(sessionId = sessionId, label = label))
            else -> store.save(s.copy(label = label))
        }
    }

    /** Forgets the timer (session finished or discarded). */
    fun clear() = write(TimerSnapshot())

    private fun clearPhases(sessionId: String?, label: String) = write(
        TimerSnapshot(sessionId = sessionId, label = label)
    )

    private fun write(snapshot: TimerSnapshot) {
        store.save(snapshot)
        val instants = snapshot.alertInstants(now())
        if (instants.isEmpty()) {
            alert.cancel()
        } else {
            val prefs = settings.settings.value
            alert.schedule(instants, snapshot.label, sound = prefs.restSound, vibrate = prefs.restVibration)
        }
    }
}
