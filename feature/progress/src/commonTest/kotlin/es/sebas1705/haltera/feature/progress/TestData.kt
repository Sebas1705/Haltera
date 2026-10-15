@file:Suppress("MatchingDeclarationName") // Shared test helpers and builders.

package es.sebas1705.haltera.feature.progress

import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.Exercise
import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.ExerciseSource
import es.sebas1705.haltera.data.models.ExerciseVariant
import es.sebas1705.haltera.data.models.Level
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.LocalizedSteps
import es.sebas1705.haltera.data.models.LocalizedText
import es.sebas1705.haltera.data.models.Muscle
import es.sebas1705.haltera.data.models.SessionExercise
import es.sebas1705.haltera.data.models.SetKind
import es.sebas1705.haltera.data.models.SetLog
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.data.models.WorkoutSession
import es.sebas1705.haltera.feature.progress.model.ProgressClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.time.ExperimentalTime

/** Sets an unconfined test dispatcher as Main so `viewModelScope` runs eagerly in tests. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class MainDispatcherTest {
    protected val dispatcher: TestDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setMain() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()
}

/** Epoch millis of a UTC date-time. */
@OptIn(ExperimentalTime::class)
fun utc(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
    LocalDateTime(year, month, day, hour, minute).toInstant(TimeZone.UTC).toEpochMilliseconds()

/** Wednesday 14 October 2026, 12:00 UTC. */
val NOW = utc(2026, 10, 14)

internal fun testClock(now: Long = NOW) = ProgressClock(nowMillis = { now }, timeZone = TimeZone.UTC)

fun exercise(id: String, vararg muscles: Muscle, equipment: List<Equipment> = listOf(Equipment.BARBELL)) = Exercise(
    id = id,
    name = LocalizedText(en = id, es = id),
    category = ExerciseCategory.STRENGTH,
    level = Level.BEGINNER,
    primaryMuscles = muscles.toList(),
    secondaryMuscles = emptyList(),
    instructions = LocalizedSteps.EMPTY,
    trackingType = TrackingType.WEIGHT_REPS,
    source = ExerciseSource.CATALOG,
    variants = equipment.map { eq ->
        ExerciseVariant(id = "$id.${eq.name.lowercase()}", exerciseId = id, equipment = eq, loadType = LoadType.BARBELL)
    },
)

fun set(loadKg: Double, reps: Int, kind: SetKind = SetKind.WORKING, completed: Boolean = true) = SetLog(
    id = "set-$loadKg-$reps-$kind",
    sessionExerciseId = "",
    position = 0,
    kind = kind,
    reps = reps,
    loadGrams = (loadKg * 1_000).toLong(),
    completed = completed,
)

fun performed(variantId: String, vararg sets: SetLog) = SessionExercise(
    id = "se-$variantId-${sets.size}",
    sessionId = "",
    position = 0,
    variantId = variantId,
    sets = sets.toList(),
)

fun session(
    id: String,
    startedAt: Long,
    vararg exercises: SessionExercise,
    durationMinutes: Long? = 60,
    routineId: String? = null,
    dayName: String? = null,
) = WorkoutSession(
    id = id,
    startedAt = startedAt,
    endedAt = durationMinutes?.let { startedAt + it * 60_000 },
    routineId = routineId,
    dayName = dayName,
    exercises = exercises.toList(),
)
