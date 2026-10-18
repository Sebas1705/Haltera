package es.sebas1705.haltera.data.repository.importer

import es.sebas1705.haltera.data.local.db.AppDatabase
import es.sebas1705.haltera.data.local.db.RoutineSlotEntity
import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.Exercise
import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.ExerciseFilter
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.LocalizedText
import es.sebas1705.haltera.data.models.NewExercise
import es.sebas1705.haltera.data.models.NewVariant
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.data.repository.ExerciseRepository
import es.sebas1705.haltera.data.repository.sql.toDb
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** The exercise a name of the file ended up as. */
private class Targets(private val byRef: Map<String, ExerciseMatch>) {
    fun of(exercise: ImportedExercise): ExerciseMatch? = byRef[ref(exercise)]

    companion object {
        fun ref(
            exercise: ImportedExercise
        ): String = "${ImportValues.matchKey(exercise.name)}|${exercise.variantId.orEmpty()}"
    }
}

private class Resolution(val targets: Targets, val matchedNames: Int, val unmatched: List<UnmatchedExercise>)

@Suppress("TooManyFunctions") // Resolve, preview and write share the helpers below.
internal class SqlHistoryImporter(
    private val db: AppDatabase,
    private val exercises: ExerciseRepository,
    private val dispatcher: CoroutineDispatcher,
) : HistoryImporter {

    override suspend fun preview(parsed: ParsedImport, options: ImportOptions): ImportPreview = withContext(
        dispatcher
    ) {
        val existing = existingSessionIds(parsed.sessions.map { it.id })
        val relevant = relevantSessions(parsed, existing, options)
        val resolution = resolve(relevant, suggest = true)
        ImportPreview(
            source = parsed.source,
            sessions = parsed.sessions.size,
            sets = parsed.setCount,
            firstStartedAt = parsed.sessions.minOfOrNull { it.startedAt },
            lastStartedAt = parsed.sessions.maxOfOrNull { it.startedAt },
            bodyWeights = parsed.bodyWeights.size,
            existingSessions = existing.size,
            existingBodyWeights = existingBodyWeightIds(parsed.bodyWeights.map { it.id }).size,
            matchedExercises = resolution.matchedNames,
            unmatched = resolution.unmatched,
            skippedRows = parsed.skippedRows,
            needsUnitChoice = parsed.needsUnitChoice,
            bodyMeasurements = parsed.bodyMeasurements.size,
            existingBodyMeasurements = existingMeasurementIds(parsed.bodyMeasurements.map { it.id }).size,
        )
    }

    override suspend fun import(parsed: ParsedImport, options: ImportOptions): ImportSummary = withContext(dispatcher) {
        val existing = existingSessionIds(parsed.sessions.map { it.id })
        val relevant = relevantSessions(parsed, existing, options)
        val resolution = resolve(relevant)
        val created = HashMap<String, ExerciseMatch>()
        val targets = HashMap<String, ExerciseMatch>()
        for (session in relevant) {
            for (exercise in session.exercises) {
                val ref = Targets.ref(exercise)
                if (ref in targets) continue
                val match = resolution.targets.of(exercise) ?: decide(exercise, relevant, options, created)
                targets[ref] = match
            }
        }
        val existingWeights = existingBodyWeightIds(parsed.bodyWeights.map { it.id })
        var sets = 0
        var weights = 0
        var measurements = 0
        db.transaction {
            for (session in relevant) {
                if (session.id in existing) db.workoutSessionEntityQueries.delete(session.id)
                sets += write(session, targets, options)
            }
            for (entry in parsed.bodyWeights) {
                if (entry.id in existingWeights && options.existing == ExistingPolicy.SKIP) continue
                db.bodyWeightEntityQueries.upsert(entry.id, entry.measuredAt, entry.weightGrams, entry.notes)
                weights++
            }
            measurements = writeMeasurements(parsed.bodyMeasurements, options)
        }
        ImportSummary(
            sessionsWritten = relevant.size - relevant.count { it.id in existing },
            sessionsSkipped = parsed.sessions.size - relevant.size,
            sessionsReplaced = relevant.count { it.id in existing },
            sets = sets,
            bodyWeights = weights,
            exercisesCreated = created.values.map { it.exerciseId }.toSet().size,
            bodyMeasurements = measurements,
        )
    }

    private fun relevantSessions(parsed: ParsedImport, existing: Set<String>, options: ImportOptions) =
        if (options.existing == ExistingPolicy.SKIP) parsed.sessions.filter { it.id !in existing } else parsed.sessions

    private fun existingSessionIds(ids: List<String>): Set<String> = ids.chunked(MAX_IN_ARGS).flatMapTo(HashSet()) {
        db.workoutSessionEntityQueries.selectExistingIds(it).executeAsList()
    }

    private fun existingBodyWeightIds(ids: List<String>): Set<String> = ids.chunked(MAX_IN_ARGS).flatMapTo(HashSet()) {
        db.bodyWeightEntityQueries.selectExistingIds(it).executeAsList()
    }

    /** Writes the measurements of the file (in the import transaction); returns how many. Existing ids follow [options]. */
    private fun writeMeasurements(measurements: List<ImportedBodyMeasurement>, options: ImportOptions): Int {
        val existing = existingMeasurementIds(measurements.map { it.id })
        var written = 0
        for (m in measurements) {
            if (m.id in existing && options.existing == ExistingPolicy.SKIP) continue
            db.bodyMeasurementEntityQueries.upsert(m.id, m.measuredAt, m.type.name, m.customName, m.value, m.notes)
            written++
        }
        return written
    }

    private fun existingMeasurementIds(ids: List<String>): Set<String> = ids.chunked(MAX_IN_ARGS).flatMapTo(HashSet()) {
        db.bodyMeasurementEntityQueries.selectExistingIds(it).executeAsList()
    }

    // region matching

    /** Matches every exercise name of [sessions]; with [suggest] the unmatched ones get an ExerciseSuggester proposal. */
    private suspend fun resolve(sessions: List<ImportedSession>, suggest: Boolean = false): Resolution {
        val all = exercises.search(ExerciseFilter(), "en", Int.MAX_VALUE).first()
        val matcher = ExerciseMatcher(all)
        val known = knownVariants(sessions)
        val byRef = HashMap<String, ExerciseMatch>()
        val unmatched = LinkedHashMap<String, UnmatchedExercise>()
        val matchedNames = HashSet<String>()
        val sessionsOf = HashMap<String, MutableSet<String>>()
        for (session in sessions) {
            for (exercise in session.exercises) {
                val ref = Targets.ref(exercise)
                val key = ImportValues.matchKey(exercise.name)
                val match = byRef[ref] ?: (
                    exercise.variantId?.let { known[it] }
                        ?: matcher.match(exercise.name, exercise.equipment ?: ExerciseMatcher.equipmentIn(exercise.name))
                    )?.also { byRef[ref] = it }
                if (match != null) {
                    matchedNames += key
                    continue
                }
                sessionsOf.getOrPut(key) { HashSet() } += session.id
                val previous = unmatched[key]
                unmatched[key] = UnmatchedExercise(
                    key = key,
                    name = previous?.name ?: exercise.name,
                    sets = (previous?.sets ?: 0) + exercise.sets.size,
                    sessions = sessionsOf.getValue(key).size,
                )
            }
        }
        val listed = if (suggest) withSuggestions(unmatched.values.toList(), all) else unmatched.values.toList()
        return Resolution(Targets(byRef), matchedNames.size, listed)
    }

    /** The unmatched names with a proposal of ExerciseSuggester (shown in the preview, applied only by the user). */
    private fun withSuggestions(unmatched: List<UnmatchedExercise>, all: List<Exercise>): List<UnmatchedExercise> {
        if (unmatched.isEmpty()) return unmatched
        val suggester = ExerciseSuggester(all)
        return unmatched.map { u -> suggester.suggest(u.name)?.let { u.copy(suggestion = it) } ?: u }
    }

    /** Variants named by a Haltera file's own ids that exist in this logbook (deprecated ones included). */
    private suspend fun knownVariants(sessions: List<ImportedSession>): Map<String, ExerciseMatch> {
        val ids = sessions.flatMap { s -> s.exercises.mapNotNull { it.variantId } }.toSet()
        if (ids.isEmpty()) return emptyMap()
        return ids.chunked(MAX_IN_ARGS).flatMap { exercises.getVariants(it) }
            .associate { it.id to ExerciseMatch(it.exerciseId, it.id) }
    }

    /** The target of an exercise nothing matched: the user's decision for its name, else a new custom exercise. */
    private suspend fun decide(
        exercise: ImportedExercise,
        sessions: List<ImportedSession>,
        options: ImportOptions,
        created: MutableMap<String, ExerciseMatch>,
    ): ExerciseMatch {
        val key = ImportValues.matchKey(exercise.name)
        val decision = options.decisions[key] ?: ExerciseDecision.CreateCustom
        if (decision is ExerciseDecision.MapTo) {
            val target = exercises.getExercise(decision.exerciseId)
            val hint = exercise.equipment ?: ExerciseMatcher.equipmentIn(exercise.name)
            val variant = target?.let { ExerciseMatcher.variantFor(it, hint) }
            if (target != null && variant != null) return ExerciseMatch(target.id, variant.id)
        }
        return created.getOrPut(key) { createCustom(exercise, sessions) }
    }

    private suspend fun createCustom(first: ImportedExercise, sessions: List<ImportedSession>): ExerciseMatch {
        val key = ImportValues.matchKey(first.name)
        val sets = sessions.flatMap { s -> s.exercises.filter { ImportValues.matchKey(it.name) == key }.flatMap { it.sets } }
        val tracking = first.trackingType ?: inferTracking(sets)
        val equipment = first.equipment ?: ExerciseMatcher.equipmentIn(first.name)
        val variant = variantFor(equipment, tracking)
        val exercise: Exercise = exercises.createCustom(
            NewExercise(
                name = LocalizedText.same(first.name.trim()),
                category = first.category ?: ExerciseCategory.STRENGTH,
                trackingType = tracking,
                primaryMuscles = first.muscles,
                variants = listOf(variant),
            ),
        )
        return ExerciseMatch(exercise.id, exercise.variants.first().id)
    }

    private fun inferTracking(sets: List<ImportedSet>): TrackingType {
        val weighted = sets.any { (it.weight ?: 0.0) != 0.0 || (it.loadGrams ?: 0L) != 0L }
        val reps = sets.any { it.reps != null }
        val timed = sets.any { it.durationS != null }
        val distance = sets.any { it.distanceM != null }
        return when {
            distance -> TrackingType.DISTANCE_TIME
            timed && !reps -> if (weighted) TrackingType.WEIGHT_TIME else TrackingType.TIME
            weighted -> TrackingType.WEIGHT_REPS
            reps -> TrackingType.BODYWEIGHT_REPS
            else -> TrackingType.WEIGHT_REPS
        }
    }

    private fun variantFor(equipment: Equipment?, tracking: TrackingType): NewVariant = when {
        equipment != null -> NewVariant(equipment, loadTypeOf(equipment))
        tracking == TrackingType.WEIGHT_REPS || tracking == TrackingType.WEIGHT_TIME ->
            NewVariant(Equipment.OTHER, LoadType.DUMBBELL)
        tracking == TrackingType.BODYWEIGHT_REPS -> NewVariant(Equipment.BODYWEIGHT, LoadType.BODYWEIGHT)
        else -> NewVariant(Equipment.NONE, LoadType.NONE)
    }

    private fun loadTypeOf(equipment: Equipment): LoadType = when (equipment) {
        Equipment.BARBELL, Equipment.EZ_BAR, Equipment.TRAP_BAR, Equipment.SMITH -> LoadType.BARBELL
        Equipment.DUMBBELL, Equipment.KETTLEBELL, Equipment.MEDICINE_BALL -> LoadType.DUMBBELL
        Equipment.MACHINE, Equipment.CABLE -> LoadType.MACHINE
        Equipment.BAND -> LoadType.BAND
        Equipment.BODYWEIGHT -> LoadType.BODYWEIGHT
        else -> LoadType.NONE
    }

    // endregion

    // region writing

    /** Inserts one session with its exercises and sets; returns the number of sets. */
    private fun write(session: ImportedSession, targets: Map<String, ExerciseMatch>, options: ImportOptions): Int {
        val routine = session.routineId?.let { db.routineEntityQueries.selectById(it).executeAsOneOrNull() }
        val routineId = routine?.id
        val dayIndex = routineId?.let { id ->
            session.routineDayIndex?.takeIf { it < db.routineDayEntityQueries.countByRoutine(id).executeAsOne() }
        }
        db.workoutSessionEntityQueries.insertImported(
            session.id,
            session.startedAt,
            session.endedAt,
            routineId,
            dayIndex?.toLong(),
            session.title,
            session.notes,
            session.bodyWeightGrams,
            // Linked: the routine's name here; otherwise the name the file kept, so the title survives.
            routine?.name ?: session.routineName,
        )
        var count = 0
        session.exercises.forEachIndexed { exerciseIndex, exercise ->
            val match = targets.getValue(Targets.ref(exercise))
            val exerciseId = "${session.id}:e$exerciseIndex"
            val slot = routineId?.let { slotOf(exercise.slotId, it) }
            db.sessionExerciseEntityQueries.insertImported(
                exerciseId,
                session.id,
                exerciseIndex.toLong(),
                match.variantId,
                slot?.id,
                exercise.notes,
                (exercise.bilateral ?: (slot?.bilateral == 1L)).toDb(),
            )
            exercise.sets.forEachIndexed { setIndex, set ->
                db.setLogEntityQueries.insert(
                    id = setId(set, "$exerciseId:s$setIndex"),
                    sessionExerciseId = exerciseId,
                    position = setIndex.toLong(),
                    kind = set.kind.name,
                    reps = set.reps?.toLong(),
                    loadGrams = set.loadGrams ?: set.weight?.let { ImportValues.grams(it, set.weightUnit ?: options.weightUnit) },
                    loadDetail = set.loadDetail,
                    durationS = set.durationS?.toLong(),
                    distanceM = set.distanceM?.toLong(),
                    rpe = set.rpe,
                    completed = 1L,
                    completedAt = set.completedAt ?: session.startedAt,
                )
                count++
            }
        }
        return count
    }

    /**
     * The id of an imported set: the file's own (Haltera exports) when it is not taken here, so replacing a session
     * keeps its set ids and sync sees updates; otherwise the id derived from the session id (Hevy, Strong, FitNotes).
     */
    private fun setId(set: ImportedSet, derived: String): String =
        set.id?.takeIf { db.setLogEntityQueries.selectById(it).executeAsOneOrNull() == null } ?: derived

    /** The slot [slotId] when it exists here and belongs to [routineId]; null otherwise. */
    private fun slotOf(slotId: String?, routineId: String): RoutineSlotEntity? {
        val slot = slotId?.let { db.routineSlotEntityQueries.selectById(it).executeAsOneOrNull() } ?: return null
        val day = db.routineDayEntityQueries.selectById(slot.dayId).executeAsOneOrNull()
        return slot.takeIf { day?.routineId == routineId }
    }

    // endregion

    private companion object {
        /** SQLite's default limit of bound arguments is 999. */
        const val MAX_IN_ARGS = 500
    }
}
