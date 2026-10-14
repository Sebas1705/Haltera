package es.sebas1705.ironlog.feature.progress.fakes

import es.sebas1705.ironlog.data.models.Exercise
import es.sebas1705.ironlog.data.models.ExerciseFilter
import es.sebas1705.ironlog.data.models.ExerciseHistoryEntry
import es.sebas1705.ironlog.data.models.ExerciseVariant
import es.sebas1705.ironlog.data.models.NewExercise
import es.sebas1705.ironlog.data.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Exercises from a fixed list; history is derived from the finished sessions of [sessions]. */
class FakeExerciseRepository(
    private val exercises: List<Exercise>,
    private val sessions: FakeSessionRepository,
) : ExerciseRepository {
    private val variants = exercises.flatMap { it.variants }.associateBy { it.id }

    override fun search(filter: ExerciseFilter, language: String, limit: Int): Flow<List<Exercise>> = flowOf(exercises)

    override fun observeExercise(id: String): Flow<Exercise?> = flowOf(exercises.firstOrNull { it.id == id })

    override suspend fun getExercise(id: String): Exercise? = exercises.firstOrNull { it.id == id }

    override suspend fun getVariant(id: String): ExerciseVariant? = variants[id]

    override suspend fun getVariants(ids: Collection<String>): List<ExerciseVariant> = ids.mapNotNull { variants[it] }

    override suspend fun createCustom(input: NewExercise): Exercise = unused()

    override fun observeVariantHistory(variantId: String, limit: Int): Flow<List<ExerciseHistoryEntry>> =
        history { it == variantId }

    override fun observeExerciseHistory(exerciseId: String, limit: Int): Flow<List<ExerciseHistoryEntry>> =
        history { variants[it]?.exerciseId == exerciseId }

    private fun history(matches: (String) -> Boolean): Flow<List<ExerciseHistoryEntry>> = sessions.sessions.map { list ->
        list.filter { !it.inProgress }
            .sortedByDescending { it.startedAt }
            .flatMap { session ->
                val performed = session.exercises.filter { matches(it.variantId) }
                performed.map { ExerciseHistoryEntry(session.id, session.startedAt, it) }
            }
    }
}
