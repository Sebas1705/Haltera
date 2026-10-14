package es.sebas1705.ironlog.data.sync

import es.sebas1705.ironlog.data.local.db.AppDatabase
import es.sebas1705.ironlog.data.sync.SyncCollections.BODY_WEIGHT
import es.sebas1705.ironlog.data.sync.SyncCollections.EXERCISES
import es.sebas1705.ironlog.data.sync.SyncCollections.ROUTINES
import es.sebas1705.ironlog.data.sync.SyncCollections.SESSIONS
import es.sebas1705.ironlog.data.sync.SyncCollections.SETTINGS

internal const val META_SYNC_APPLYING = "syncApplying"

/**
 * Reads aggregates into payloads and applies remote documents (design §3, §5).
 *
 * Apply runs in one transaction with Meta `syncApplying = '1'`, so the change-tracking triggers stay quiet,
 * and records the document's `updatedAt` as the root's clean SyncState. Children missing from the payload are
 * deleted and the rest upserted in place (ids are stable, so session links to routine slots survive an edit).
 */
internal class AggregateStore(
    private val db: AppDatabase,
    private val settings: SettingsSyncBridge,
) {
    private val q get() = db.syncTrackingQueries

    /** The payload of a local root, or null when it does not exist (upload a tombstone). */
    fun read(collection: String, id: String): String? = when (collection) {
        ROUTINES -> readRoutine(id)?.let { SyncJson.encodeToString(RoutinePayload.serializer(), it) }
        SESSIONS -> readSession(id)?.let { SyncJson.encodeToString(SessionPayload.serializer(), it) }
        BODY_WEIGHT -> q.selectBodyWeight(id).executeAsOneOrNull()
            ?.let { SyncJson.encodeToString(BodyWeightPayload.serializer(), it.toPayload()) }
        EXERCISES -> readExercise(id)?.let { SyncJson.encodeToString(ExercisePayload.serializer(), it) }
        SETTINGS -> SyncJson.encodeToString(
            SettingsPayload.serializer(),
            SettingsPayload(settings.prefs(), db.routineEntityQueries.selectActive().executeAsOneOrNull()?.id),
        )
        else -> null
    }

    /** Applies [doc] and marks its root clean at the document's `updatedAt`. Throws (rolled back) on failure. */
    fun apply(doc: SyncDocument) {
        var prefs: SettingsPrefs? = null
        db.transaction {
            db.metaQueries.put(META_SYNC_APPLYING, "1")
            if (doc.deleted) {
                delete(doc.collection, doc.id)
            } else {
                prefs = write(doc)
            }
            q.putState(doc.collection, doc.id, doc.updatedAt, 0, if (doc.deleted) 1 else 0)
            db.metaQueries.put(META_SYNC_APPLYING, "0")
        }
        prefs?.let(settings::writeBack)
    }

    private fun delete(collection: String, id: String) {
        when (collection) {
            ROUTINES -> db.routineEntityQueries.delete(id)
            SESSIONS -> db.workoutSessionEntityQueries.delete(id)
            BODY_WEIGHT -> db.bodyWeightEntityQueries.delete(id)
            EXERCISES -> {
                q.deleteUserVariantsNotIn(id, emptyList())
                q.deleteUserExercise(id)
            }
            else -> Unit // settings/user is never deleted
        }
    }

    /** Returns the settings to write back once the transaction commits, for `settings/user`. */
    private fun write(doc: SyncDocument): SettingsPrefs? {
        when (doc.collection) {
            ROUTINES -> writeRoutine(SyncJson.decodeFromString(RoutinePayload.serializer(), doc.payload))
            SESSIONS -> writeSession(SyncJson.decodeFromString(SessionPayload.serializer(), doc.payload))
            BODY_WEIGHT -> SyncJson.decodeFromString(BodyWeightPayload.serializer(), doc.payload).let {
                db.bodyWeightEntityQueries.upsert(it.id, it.measuredAt, it.weightGrams, it.notes)
            }
            EXERCISES -> writeExercise(SyncJson.decodeFromString(ExercisePayload.serializer(), doc.payload))
            SETTINGS -> {
                val payload = SyncJson.decodeFromString(SettingsPayload.serializer(), doc.payload)
                db.routineEntityQueries.clearActive()
                payload.activeRoutineId?.let(db.routineEntityQueries::markActive)
                return payload.prefs
            }
        }
        return null
    }

    // Routines

    private fun readRoutine(id: String): RoutinePayload? {
        val routine = db.routineEntityQueries.selectById(id).executeAsOneOrNull() ?: return null
        val days = db.routineDayEntityQueries.selectByRoutine(id).executeAsList()
        val slots = db.routineSlotEntityQueries.selectByDays(days.map { it.id }).executeAsList().groupBy { it.dayId }
        return routine.toPayload(days.map { day -> day.toPayload(slots[day.id].orEmpty().map { it.toPayload() }) })
    }

    private fun writeRoutine(r: RoutinePayload) {
        q.insertRoutineIfMissing(
            r.id,
            r.name,
            r.description,
            r.originTemplateId,
            r.originVersion,
            r.nextDayIndex,
            r.createdAt,
            r.updatedAt
        )
        q.updateRoutine(
            r.name,
            r.description,
            r.originTemplateId,
            r.originVersion,
            r.nextDayIndex,
            r.createdAt,
            r.updatedAt,
            r.id
        )
        q.deleteDaysNotIn(r.id, r.days.map { it.id })
        r.days.forEach { d ->
            q.insertDayIfMissing(d.id, r.id, d.position, d.name, d.kind)
            q.updateDay(r.id, d.position, d.name, d.kind, d.id)
            q.deleteSlotsNotIn(d.id, d.slots.map { it.id })
            d.slots.forEach { s ->
                q.insertSlotIfMissing(
                    s.id, d.id, s.position, s.target, s.sets, s.warmupSets, s.prescription, s.restBetweenSetsS,
                    s.restAfterS, s.supersetGroup, s.bilateral, s.notes, s.progression,
                )
                q.updateSlot(
                    d.id, s.position, s.target, s.sets, s.warmupSets, s.prescription, s.restBetweenSetsS,
                    s.restAfterS, s.supersetGroup, s.bilateral, s.notes, s.progression, s.id,
                )
            }
        }
    }

    // Sessions

    private fun readSession(id: String): SessionPayload? {
        val session = db.workoutSessionEntityQueries.selectById(id).executeAsOneOrNull() ?: return null
        val exercises = db.sessionExerciseEntityQueries.selectBySession(id).executeAsList()
        val sets = db.setLogEntityQueries.selectBySessionExercises(exercises.map { it.id }).executeAsList()
            .groupBy { it.sessionExerciseId }
        return session.toPayload(exercises.map { e -> e.toPayload(sets[e.id].orEmpty().map { it.toPayload() }) })
    }

    private fun writeSession(s: SessionPayload) {
        // Links to a routine or slot that does not exist here (deleted, or not synced yet) are dropped,
        // as ON DELETE SET NULL would have done.
        val routineId = s.routineId?.takeIf { q.routineExists(it).executeAsOne() > 0 }
        q.insertSessionIfMissing(
            s.id,
            s.startedAt,
            s.endedAt,
            routineId,
            s.routineDayIndex,
            s.dayName,
            s.notes,
            s.bodyWeightGrams
        )
        q.updateSession(
            s.startedAt,
            s.endedAt,
            routineId,
            s.routineDayIndex,
            s.dayName,
            s.notes,
            s.bodyWeightGrams,
            s.id
        )
        q.deleteSessionExercisesNotIn(s.id, s.exercises.map { it.id })
        s.exercises.forEach { e ->
            val slotId = e.slotId?.takeIf { q.slotExists(it).executeAsOne() > 0 }
            q.insertSessionExerciseIfMissing(e.id, s.id, e.position, e.variantId, slotId, e.notes)
            q.updateSessionExercise(s.id, e.position, e.variantId, slotId, e.notes, e.id)
            q.deleteSetsNotIn(e.id, e.sets.map { it.id })
            e.sets.forEach { t ->
                q.replaceSet(
                    t.id, e.id, t.position, t.kind, t.reps, t.loadGrams, t.loadDetail, t.durationS, t.distanceM,
                    t.rpe, t.completed, t.completedAt,
                )
            }
        }
    }

    // USER exercises

    private fun readExercise(id: String): ExercisePayload? {
        val exercise = db.exerciseEntityQueries.selectById(id).executeAsOneOrNull()
            ?.takeIf { it.source == USER } ?: return null
        val variants = db.exerciseVariantEntityQueries.selectByExercise(id).executeAsList().filter { it.source == USER }
        return exercise.toPayload(variants.map { it.toPayload() })
    }

    private fun writeExercise(e: ExercisePayload) {
        q.insertExerciseIfMissing(
            e.id, e.nameEn, e.nameEs, e.nameEnSearch, e.nameEsSearch, e.category, e.trackingType, e.level,
            e.primaryMuscles, e.secondaryMuscles, e.instructionsEn, e.instructionsEs, e.deprecated, e.updatedAt,
        )
        q.updateUserExercise(
            e.nameEn, e.nameEs, e.nameEnSearch, e.nameEsSearch, e.category, e.trackingType, e.level,
            e.primaryMuscles, e.secondaryMuscles, e.instructionsEn, e.instructionsEs, e.deprecated, e.updatedAt, e.id,
        )
        q.deleteUserVariantsNotIn(e.id, e.variants.map { it.id })
        e.variants.forEach { v ->
            q.insertVariantIfMissing(
                v.id, e.id, v.position, v.equipment, v.loadType, v.nameEn, v.nameEs, v.instructionsEn,
                v.instructionsEs, v.frames, v.deprecated, v.updatedAt,
            )
            q.updateUserVariant(
                e.id, v.position, v.equipment, v.loadType, v.nameEn, v.nameEs, v.instructionsEn, v.instructionsEs,
                v.frames, v.deprecated, v.updatedAt, v.id,
            )
        }
    }

    private companion object {
        const val USER = "USER"
    }
}
