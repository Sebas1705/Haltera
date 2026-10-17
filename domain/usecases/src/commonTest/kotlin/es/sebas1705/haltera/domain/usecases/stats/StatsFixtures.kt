package es.sebas1705.haltera.domain.usecases.stats

import es.sebas1705.haltera.data.models.ExerciseHistoryEntry
import es.sebas1705.haltera.data.models.SessionExercise
import es.sebas1705.haltera.data.models.SetKind
import es.sebas1705.haltera.data.models.SetLog
import es.sebas1705.haltera.data.models.WorkoutSession

internal const val DAY_MS = 86_400_000L

/** Wednesday 14 October 2026 as an epoch day. */
internal const val TODAY = 20_740

/** A set of [kg] × [reps]. */
internal fun lift(
    kg: Double?,
    reps: Int?,
    kind: SetKind = SetKind.WORKING,
    completed: Boolean = true,
    detail: String? = null,
    seconds: Int? = null,
) = SetLog(
    id = "s",
    sessionExerciseId = "e",
    position = 0,
    kind = kind,
    reps = reps,
    loadGrams = kg?.let { (it * 1_000).toLong() },
    loadDetail = detail,
    durationS = seconds,
    completed = completed,
)

/** A performed exercise; [bilateral] is its "Both hands" snapshot (taken from the slot when it was logged). */
internal fun move(variantId: String, vararg sets: SetLog, slotId: String? = null, bilateral: Boolean = false) =
    SessionExercise(
        id = "se-$variantId",
        sessionId = "",
        position = 0,
        variantId = variantId,
        slotId = slotId,
        sets = sets.toList(),
        bilateral = bilateral,
    )

/** A session on epoch [day] at 10:00 UTC lasting [minutes] (null: in progress). */
internal fun dated(id: String, day: Int, vararg exercises: SessionExercise, minutes: Long? = 60): DatedSession {
    val start = day * DAY_MS + 10 * 3_600_000L
    return DatedSession(
        day,
        WorkoutSession(
            id = id,
            startedAt = start,
            endedAt = minutes?.let {
                start + it * 60_000
            },
            exercises = exercises.toList()
        ),
    )
}

/** One variant's history entry on epoch [day]. */
internal fun historyEntry(session: String, day: Int, vararg sets: SetLog, variantId: String = "squat.barbell") =
    ExerciseHistoryEntry(
        session,
        day * DAY_MS,
        SessionExercise("e$session", session, 0, variantId, sets = sets.toList())
    )
