package es.sebas1705.haltera.feature.routines.model

import es.sebas1705.haltera.data.models.Routine
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.data.models.WorkoutSession
import es.sebas1705.haltera.data.repository.ExerciseRepository
import es.sebas1705.haltera.domain.usecases.routinescore.Advice
import es.sebas1705.haltera.domain.usecases.routinescore.RoutineScore
import es.sebas1705.haltera.domain.usecases.routinescore.RoutineScorer
import es.sebas1705.haltera.domain.usecases.routinescore.ScoringCatalogue

/** Scores [routine] with the exercises of [index] (they must include the routine's exercises). */
internal fun scoreRoutine(
    routine: Routine,
    index: ExerciseIndex,
    sessions: List<WorkoutSession> = emptyList(),
    now: Long = 0L,
): RoutineScore = RoutineScorer.score(routine, ScoringCatalogue(index.exercises.values), sessions, now)

/** Every slot target of [routines], to load their exercises in one go. */
internal fun targetsOf(routines: Collection<Routine>): List<SlotTarget> =
    routines.flatMap { routine -> routine.days.flatMap { day -> day.slots.map { it.target } } }

/** The catalogue exercises the advice of [score] names. */
internal fun suggestedExerciseIds(
    score: RoutineScore
): Set<String> = score.criteria.flatMap { it.advice }.flatMap { advice ->
    when (advice) {
        is Advice.AddSets -> advice.exerciseIds
        is Advice.Cover -> advice.exerciseIds
        is Advice.Rebalance -> advice.exerciseIds
        is Advice.AddHeavyCompound -> advice.exerciseIds
        is Advice.BreakPlateau -> advice.exerciseIds
        else -> emptyList()
    }
}.toSet()

/** [known] plus the exercises with ids [exerciseIds], loaded from the repository. */
internal suspend fun ExerciseRepository.indexForExercises(
    exerciseIds: Collection<String>,
    known: ExerciseIndex
): ExerciseIndex =
    indexFor(exerciseIds.map { SlotTarget.Flexible(it) }, known)
