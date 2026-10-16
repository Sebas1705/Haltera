package es.sebas1705.haltera.data.repository.sql

import es.sebas1705.haltera.core.search.TextNormalizer
import es.sebas1705.haltera.data.local.SearchText
import es.sebas1705.haltera.data.local.db.AppDatabase
import es.sebas1705.haltera.data.local.db.ExerciseEntity
import es.sebas1705.haltera.data.models.DeleteOutcome
import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.Exercise
import es.sebas1705.haltera.data.models.ExerciseEdit
import es.sebas1705.haltera.data.models.ExerciseFilter
import es.sebas1705.haltera.data.models.ExerciseHistoryEntry
import es.sebas1705.haltera.data.models.ExerciseSource
import es.sebas1705.haltera.data.models.ExerciseUsage as CustomExerciseUsage
import es.sebas1705.haltera.data.models.ExerciseVariant
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.ModelJson
import es.sebas1705.haltera.data.models.Muscle
import es.sebas1705.haltera.data.models.NewExercise
import es.sebas1705.haltera.data.models.NewVariant
import es.sebas1705.haltera.data.models.RoutineUse
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.data.repository.ExerciseRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Suppress("TooManyFunctions") // Catalogue reads plus the custom exercise lifecycle.
internal class SqlExerciseRepository(
    private val db: AppDatabase,
    private val history: HistoryLoader,
    private val dispatcher: CoroutineDispatcher,
    private val now: () -> Long,
) : ExerciseRepository {

    /** Fuzzy index of every active exercise per app language, rebuilt when exercises or variants change. */
    private val searchIndex = SearchIndexCache<String, ExerciseSearch>(
        watched = listOf(db.exerciseEntityQueries.selectActive(), db.exerciseVariantEntityQueries.countActive()),
    ) { language ->
        ExerciseSearch(
            withVariants(db.exerciseEntityQueries.selectActive().executeAsList(), includeDeprecated = false),
            language
        )
    }

    override fun search(filter: ExerciseFilter, language: String, limit: Int): Flow<List<Exercise>> {
        val lang = if (language.lowercase().startsWith("es")) "es" else "en"
        if (TextNormalizer.fold(filter.query).isBlank()) return browse(filter, lang, limit)
        // A text query is ranked in memory (ExerciseSearch): order-independent, typo tolerant, with synonyms.
        val usage = db.exerciseEntityQueries.usageStats()
        return tablesChanged(
            db.exerciseEntityQueries.selectActive(),
            db.exerciseVariantEntityQueries.countActive(),
            usage
        )
            .conflate()
            .map {
                val stats = usage.executeAsList().associate { it.exerciseId to ExerciseUsage(it.sessions, it.lastStartedAt ?: 0L) }
                searchIndex.get(lang).search(filter, limit, stats, now())
            }
            .flowOn(dispatcher)
    }

    /** No text query: the filters only, most logged first (SQL `search` with an empty query). */
    private fun browse(filter: ExerciseFilter, language: String, limit: Int): Flow<List<Exercise>> {
        val query = db.exerciseEntityQueries.search(
            query = "",
            category = filter.category?.name,
            muscle = filter.muscle?.name,
            equipment = filter.equipment?.name,
            language = language,
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
            if (input.author != null || input.originId != null) {
                db.exerciseEntityQueries.setShareOrigin(
                    author = input.author,
                    originId = input.originId,
                    id = exerciseId
                )
            }
            val usedIds = mutableSetOf<String>()
            variants.forEachIndexed { position, variant ->
                db.exerciseVariantEntityQueries.insertUser(
                    id = variantId(exerciseId, variant.equipment, usedIds),
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

    override suspend fun updateCustom(id: String, edit: ExerciseEdit): Exercise = withContext(dispatcher) {
        val current = requireNotNull(loadExercise(id)?.takeIf { it.source == ExerciseSource.USER }) {
            "Not a custom exercise: $id"
        }
        val logged = db.exerciseEntityQueries.variantsInSessions(id).executeAsList().toSet()
        validate(current, edit, logged)
        val keep = logged + routineSlotsUsing(id).flatMap { it.variantIds }
        val at = now()
        db.transaction {
            db.exerciseEntityQueries.updateUser(
                nameEn = edit.name.en.trim(),
                nameEs = edit.name.es.trim(),
                nameEnSearch = SearchText.normalize(edit.name.en),
                nameEsSearch = SearchText.normalize(edit.name.es),
                category = edit.category.name,
                trackingType = edit.trackingType.name,
                level = edit.level.name,
                primaryMuscles = Muscle.listToDb(edit.primaryMuscles),
                secondaryMuscles = Muscle.listToDb(edit.secondaryMuscles),
                instructionsEn = stepsToDb(edit.instructions.en),
                instructionsEs = stepsToDb(edit.instructions.es),
                updatedAt = at,
                id = id,
            )
            writeVariants(current, edit, keep, at)
        }
        checkNotNull(loadExercise(id))
    }

    /** The checks of [updateCustom]: well-formed, and nothing [logged] variants depend on changes. */
    private fun validate(current: Exercise, edit: ExerciseEdit, logged: Set<String>) {
        require(edit.name.en.isNotBlank() && edit.name.es.isNotBlank()) { "Blank name" }
        require(edit.variants.isNotEmpty()) { "A custom exercise needs a variant" }
        val byId = current.variants.associateBy { it.id }
        edit.variants.forEach { v ->
            require(v.id == null || v.id in byId) { "Variant ${v.id} is not of ${current.id}" }
        }
        val keptIds = edit.variants.mapNotNull { it.id }
        require(keptIds.size == keptIds.toSet().size) { "Repeated variant" }
        check(logged.isEmpty() || edit.trackingType == current.trackingType) {
            "The tracking type of a logged exercise is fixed"
        }
        edit.variants.forEach { v ->
            val old = v.id?.let(byId::get) ?: return@forEach
            check(old.id !in logged || (old.equipment == v.equipment && old.loadType == v.loadType)) {
                "Equipment and load type of logged variant ${old.id} are fixed"
            }
        }
    }

    /**
     * Updates the kept variants in the edit order, inserts the new ones, and deletes the left-out ones unless
     * [referenced] (by a session or a routine): those are deprecated after the active ones.
     */
    private fun writeVariants(current: Exercise, edit: ExerciseEdit, referenced: Set<String>, at: Long) {
        val usedIds = current.variants.map { it.id }.toMutableSet()
        edit.variants.forEachIndexed { position, v ->
            val existingId = v.id
            if (existingId != null) {
                db.exerciseVariantEntityQueries.updateUser(
                    position = position.toLong(),
                    equipment = v.equipment.name,
                    loadType = v.loadType.name,
                    nameEn = v.name?.en,
                    nameEs = v.name?.es,
                    deprecated = 0,
                    updatedAt = at,
                    id = existingId,
                )
            } else {
                db.exerciseVariantEntityQueries.insertUser(
                    id = variantId(current.id, v.equipment, usedIds),
                    exerciseId = current.id,
                    position = position.toLong(),
                    equipment = v.equipment.name,
                    loadType = v.loadType.name,
                    nameEn = v.name?.en,
                    nameEs = v.name?.es,
                    updatedAt = at,
                )
            }
        }
        val keptIds = edit.variants.mapNotNull { it.id }.toSet()
        current.variants.filter { it.id !in keptIds }.forEachIndexed { i, old ->
            if (old.id in referenced) {
                // Still referenced: hidden from pickers while history and routines keep resolving it.
                db.exerciseVariantEntityQueries.updateUser(
                    position = (edit.variants.size + i).toLong(),
                    equipment = old.equipment.name,
                    loadType = old.loadType.name,
                    nameEn = old.name?.en,
                    nameEs = old.name?.es,
                    deprecated = 1,
                    updatedAt = at,
                    id = old.id,
                )
            } else {
                db.exerciseVariantEntityQueries.deleteUser(old.id)
            }
        }
    }

    override suspend fun usage(id: String): CustomExerciseUsage = withContext(dispatcher) {
        if (db.exerciseEntityQueries.selectById(id).executeAsOneOrNull() == null) return@withContext CustomExerciseUsage()
        val slots = routineSlotsUsing(id)
        val counts = db.exerciseEntityQueries.historyCounts(id).executeAsOne()
        CustomExerciseUsage(
            routines = slots.groupBy { it.routineId }.map { (routineId, s) ->
                RoutineUse(routineId, s.first().routineName, s.size)
            },
            sessions = counts.sessions.toInt(),
            sets = counts.sets.toInt(),
            variantsWithHistory = db.exerciseEntityQueries.variantsInSessions(id).executeAsList().toSet(),
            variantsInRoutines = slots.flatMap { it.variantIds }.toSet(),
        )
    }

    override suspend fun deleteCustom(id: String, removeFromRoutines: Boolean): DeleteOutcome =
        withContext(dispatcher) {
            val row = db.exerciseEntityQueries.selectById(id).executeAsOneOrNull()
            require(row != null && ExerciseSource.fromDb(row.source) == ExerciseSource.USER) {
                "Not a custom exercise: $id"
            }
            val slots = routineSlotsUsing(id)
            if (slots.isNotEmpty() && !removeFromRoutines) return@withContext DeleteOutcome.USED_IN_ROUTINES
            val at = now()
            db.transactionWithResult {
                removeSlots(slots, at)
                if (db.exerciseEntityQueries.variantsInSessions(id).executeAsList().isNotEmpty()) {
                    db.exerciseEntityQueries.setUserDeprecated(deprecated = 1, updatedAt = at, id = id)
                    DeleteOutcome.HIDDEN
                } else {
                    db.exerciseVariantEntityQueries.selectByExercise(id).executeAsList()
                        .forEach { db.exerciseVariantEntityQueries.deleteUser(it.id) }
                    db.exerciseEntityQueries.deleteUser(id)
                    DeleteOutcome.DELETED
                }
            }
        }

    override suspend fun restoreCustom(id: String) = withContext(dispatcher) {
        db.exerciseEntityQueries.setUserDeprecated(deprecated = 0, updatedAt = now(), id = id)
        Unit
    }

    override suspend fun findByOrigin(originId: String): Exercise? = withContext(dispatcher) {
        db.exerciseEntityQueries.selectUserByOrigin(originId).executeAsOneOrNull()
            ?.let { withVariants(listOf(it), includeDeprecated = true).single() }
    }

    /** Removes [slots] from their routines, keeping slot positions contiguous and bumping each routine. */
    private fun removeSlots(slots: List<SlotUse>, at: Long) {
        slots.forEach { db.routineSlotEntityQueries.delete(it.slotId) }
        slots.map { it.dayId }.distinct().forEach { dayId ->
            db.routineSlotEntityQueries.selectByDay(dayId).executeAsList().forEachIndexed { index, s ->
                db.routineSlotEntityQueries.setPosition(index.toLong(), s.id)
            }
        }
        slots.map { it.routineId }.distinct().forEach { db.routineEntityQueries.touch(updatedAt = at, id = it) }
    }

    /** A routine slot whose target uses the exercise, with the variant ids it names. */
    private data class SlotUse(
        val slotId: String,
        val dayId: String,
        val routineId: String,
        val routineName: String,
        val variantIds: List<String>,
    )

    private fun routineSlotsUsing(exerciseId: String): List<SlotUse> {
        val variantIds = db.exerciseVariantEntityQueries.selectByExercise(exerciseId).executeAsList()
            .map { it.id }
            .toSet()
        return db.exerciseEntityQueries.slotsMentioning(exerciseId).executeAsList().mapNotNull { row ->
            val named = when (val target = ModelJson.decodeTarget(row.target)) {
                is SlotTarget.Fixed -> listOf(target.variantId).takeIf { target.variantId in variantIds }
                is SlotTarget.Flexible ->
                    target.allowedVariantIds.filter { it in variantIds }.takeIf { target.exerciseId == exerciseId }
                null -> null
            }
            named?.let { SlotUse(row.id, row.dayId, row.routineId, row.routineName, it) }
        }
    }

    /** `<exerciseId>.<equipment>`, with a `-2`, `-3`… suffix when [usedIds] already has it; adds it to [usedIds]. */
    private fun variantId(exerciseId: String, equipment: Equipment, usedIds: MutableSet<String>): String {
        val base = "$exerciseId.${equipment.name.lowercase().replace('_', '-')}"
        var id = base
        var suffix = 2
        while (!usedIds.add(id)) id = "$base-${suffix++}"
        return id
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
