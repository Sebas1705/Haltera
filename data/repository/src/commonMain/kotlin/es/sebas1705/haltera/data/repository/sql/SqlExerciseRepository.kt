package es.sebas1705.haltera.data.repository.sql

import es.sebas1705.haltera.data.local.SearchText
import es.sebas1705.haltera.data.local.db.AppDatabase
import es.sebas1705.haltera.data.local.db.ExerciseEntity
import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.Exercise
import es.sebas1705.haltera.data.models.ExerciseFilter
import es.sebas1705.haltera.data.models.ExerciseHistoryEntry
import es.sebas1705.haltera.data.models.ExerciseVariant
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.Muscle
import es.sebas1705.haltera.data.models.NewExercise
import es.sebas1705.haltera.data.models.NewVariant
import es.sebas1705.haltera.data.repository.ExerciseRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class SqlExerciseRepository(
    private val db: AppDatabase,
    private val history: HistoryLoader,
    private val dispatcher: CoroutineDispatcher,
    private val now: () -> Long,
) : ExerciseRepository {

    override fun search(filter: ExerciseFilter, language: String, limit: Int): Flow<List<Exercise>> {
        val query = db.exerciseEntityQueries.search(
            query = SearchText.query(filter.query),
            category = filter.category?.name,
            muscle = filter.muscle?.name,
            equipment = filter.equipment?.name,
            language = if (language.lowercase().startsWith("es")) "es" else "en",
            limit = limit.toLong(),
        )
        return tablesChanged(query).conflate()
            .map { withVariants(query.executeAsList(), includeDeprecated = false) }
            .flowOn(dispatcher)
    }

    override fun observeExercise(id: String): Flow<Exercise?> =
        tablesChanged(db.exerciseEntityQueries.selectById(id), db.exerciseVariantEntityQueries.selectById(id))
            .conflate()
            .map { loadExercise(id) }
            .flowOn(dispatcher)

    override suspend fun getExercise(id: String): Exercise? = withContext(dispatcher) { loadExercise(id) }

    override suspend fun getVariant(id: String): ExerciseVariant? = withContext(dispatcher) {
        db.exerciseVariantEntityQueries.selectById(id).executeAsOneOrNull()?.toModel()
    }

    override suspend fun getVariants(ids: Collection<String>): List<ExerciseVariant> = withContext(dispatcher) {
        if (ids.isEmpty()) {
            emptyList()
        } else {
            db.exerciseVariantEntityQueries.selectByIds(
                ids
            ).executeAsList().map { it.toModel() }
        }
    }

    override suspend fun createCustom(input: NewExercise): Exercise = withContext(dispatcher) {
        val exerciseId = "user-${newId()}"
        val at = now()
        val variants = input.variants.ifEmpty { listOf(NewVariant(Equipment.NONE, LoadType.NONE)) }
        db.transaction {
            db.exerciseEntityQueries.insertUser(
                id = exerciseId,
                nameEn = input.name.en,
                nameEs = input.name.es,
                nameEnSearch = SearchText.normalize(input.name.en),
                nameEsSearch = SearchText.normalize(input.name.es),
                category = input.category.name,
                trackingType = input.trackingType.name,
                level = input.level.name,
                primaryMuscles = Muscle.listToDb(input.primaryMuscles),
                secondaryMuscles = Muscle.listToDb(input.secondaryMuscles),
                instructionsEn = stepsToDb(input.instructions.en),
                instructionsEs = stepsToDb(input.instructions.es),
                updatedAt = at,
            )
            val usedIds = mutableSetOf<String>()
            variants.forEachIndexed { position, variant ->
                val base = "$exerciseId.${variant.equipment.name.lowercase().replace('_', '-')}"
                var variantId = base
                var suffix = 2
                while (!usedIds.add(variantId)) variantId = "$base-${suffix++}"
                db.exerciseVariantEntityQueries.insertUser(
                    id = variantId,
                    exerciseId = exerciseId,
                    position = position.toLong(),
                    equipment = variant.equipment.name,
                    loadType = variant.loadType.name,
                    nameEn = variant.name?.en,
                    nameEs = variant.name?.es,
                    updatedAt = at,
                )
            }
        }
        checkNotNull(loadExercise(exerciseId))
    }

    override fun observeVariantHistory(variantId: String, limit: Int): Flow<List<ExerciseHistoryEntry>> =
        history.changes().conflate().map { history.byVariant(variantId, limit) }.flowOn(dispatcher)

    override fun observeExerciseHistory(exerciseId: String, limit: Int): Flow<List<ExerciseHistoryEntry>> =
        history.changes().conflate().map { history.byExercise(exerciseId, limit) }.flowOn(dispatcher)

    private fun loadExercise(id: String): Exercise? =
        db.exerciseEntityQueries.selectById(
            id
        ).executeAsOneOrNull()?.let { withVariants(listOf(it), includeDeprecated = true).single() }

    private fun withVariants(rows: List<ExerciseEntity>, includeDeprecated: Boolean): List<Exercise> {
        if (rows.isEmpty()) return emptyList()
        val variants = rows.map { it.id }.chunked(MAX_IN_ARGS).flatMap { ids ->
            db.exerciseVariantEntityQueries.selectByExercises(ids).executeAsList()
        }
            .map { it.toModel() }
            .filter { includeDeprecated || !it.deprecated }
            .groupBy { it.exerciseId }
        return rows.map { row -> row.toModel(variants[row.id].orEmpty().sortedBy { it.position }) }
    }

    private companion object {
        /** SQLite's default limit of bound arguments is 999. */
        const val MAX_IN_ARGS = 500
    }
}
