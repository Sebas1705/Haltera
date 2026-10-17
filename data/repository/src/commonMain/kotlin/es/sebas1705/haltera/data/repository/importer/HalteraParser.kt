package es.sebas1705.haltera.data.repository.importer

import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.Muscle
import es.sebas1705.haltera.data.models.SetKind
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.data.repository.share.SharedExercise
import kotlin.math.roundToLong
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private const val GRAMS_PER_KG = 1000.0

/** Reader of the Haltera export files (docs/EXPORT-FORMAT.md): `sessions.csv` and `body_weight.csv`. */
@OptIn(ExperimentalTime::class)
internal object HalteraParser {

    private fun instant(text: String): Long? =
        if (text.isEmpty()) null else runCatching { Instant.parse(text).toEpochMilliseconds() }.getOrNull()

    /** The sessions of `sessions.csv`; [definitions] are the user's exercises of `exercises.json`, by id. */
    fun sessions(table: CsvTable, definitions: Map<String, SharedExercise>): ParsedImport {
        val acc = SessionAccumulator(ImportSource.HALTERA)
        var skipped = 0
        val explicitEnd = mutableSetOf<String>()
        for (row in table.rows()) {
            val startedAt = instant(row["started_at"])
            val name = row["exercise"].ifEmpty { row["exercise_id"] }
            if (startedAt == null || name.isEmpty()) {
                skipped++
                continue
            }
            val id = row["session_id"].ifEmpty {
                ImportValues.stableId(
                    ImportSource.HALTERA.name,
                    startedAt.toString()
                )
            }
            val group = acc.group(id) {
                SessionAccumulator.Group(
                    startText = id,
                    title = row["day_name"].ifEmpty { null },
                    startedAt = startedAt,
                    fixedId = id,
                )
            }
            extendEnd(group, row, explicitEnd)
            if (group.notes.isEmpty()) group.notes = row["session_notes"]
            link(group, row)
            if (group.bodyWeightGrams == null) {
                group.bodyWeightGrams = ImportValues.decimal(row["session_body_weight_kg"])
                    ?.let { (it * GRAMS_PER_KG).roundToLong() }
            }
            val variantId = row["variant_id"].ifEmpty { null }
            val exercise = group.exercise("${row["exercise_position"]}|$variantId|$name", name)
            row["exercise_notes"].takeIf { it.isNotEmpty() }?.let { exercise.notes += it }
            describe(exercise, row["exercise_id"].ifEmpty { null }, variantId, definitions)
            if (exercise.slotId == null) exercise.slotId = row["slot_id"].ifEmpty { null }
            if (exercise.bilateral == null) exercise.bilateral = flag(row["both_hands"])
            exercise.sets += set(row, startedAt)
        }
        return ParsedImport(ImportSource.HALTERA, acc.build(), skippedRows = skipped)
    }

    /**
     * The end of [group] is `ended_at`; only a session without one ends at its last `completed_at`. The sets of a past
     * session logged later are completed after the session ended and must not stretch it. [explicitEnd] holds the
     * sessions that had an `ended_at`.
     */
    private fun extendEnd(group: SessionAccumulator.Group, row: CsvTable.Row, explicitEnd: MutableSet<String>) {
        val id = group.startText
        val endedAt = instant(row["ended_at"])
        if (endedAt != null) {
            group.endedAt = if (id in explicitEnd) maxOf(group.endedAt ?: 0L, endedAt) else endedAt
            explicitEnd += id
        } else if (id !in explicitEnd) {
            group.endedAt = maxOf(group.endedAt ?: 0L, instant(row["completed_at"]) ?: 0L).takeIf { it > 0 }
        }
    }

    /** Version 2 columns: the routine, its name and the day the session was logged from (version 1 has none). */
    private fun link(group: SessionAccumulator.Group, row: CsvTable.Row) {
        if (group.routineId == null) group.routineId = row["routine_id"].ifEmpty { null }
        if (group.routineName == null) group.routineName = row["routine_name"].ifEmpty { null }
        if (group.routineDayIndex == null) group.routineDayIndex = row["routine_day_index"].toIntOrNull()?.takeIf { it >= 0 }
    }

    /** `both_hands`: 1 or 0; empty (version 1) or anything else is unknown. */
    private fun flag(text: String): Boolean? = when (text) {
        "1" -> true
        "0" -> false
        else -> null
    }

    private fun set(row: CsvTable.Row, startedAt: Long) = ImportedSet(
        id = row["set_id"].ifEmpty { null },
        kind = SetKind.fromDb(row["set_type"]),
        reps = ImportValues.integer(row["reps"]),
        loadGrams = row["load_grams"].takeIf { it.isNotEmpty() }
            ?.let { it.toLongOrNull() ?: ImportValues.decimal(it)?.roundToLong() },
        loadDetail = row["load_detail"].ifEmpty { null },
        durationS = ImportValues.integer(row["duration_s"]),
        distanceM = ImportValues.integer(row["distance_m"]),
        rpe = ImportValues.rpe(row["rpe"]),
        completedAt = instant(row["completed_at"]) ?: startedAt,
    )

    private fun describe(
        exercise: SessionAccumulator.ExerciseAcc,
        exerciseId: String?,
        variantId: String?,
        definitions: Map<String, SharedExercise>,
    ) {
        exercise.exerciseId = exerciseId
        exercise.variantId = variantId
        val definition = exerciseId?.let { definitions[it] } ?: return
        exercise.category = ExerciseCategory.fromDb(definition.category)
        exercise.trackingType = TrackingType.fromDb(definition.trackingType)
        exercise.muscles = Muscle.listFromDb(definition.primaryMuscles.joinToString(","))
        exercise.equipment = definition.variants.firstOrNull { it.id == variantId }?.let { Equipment.fromDb(it.equipment) }
    }

    /** The entries of `body_weight.csv`; rows without a time or a weight are dropped. */
    fun bodyWeights(table: CsvTable): List<ImportedBodyWeight> = table.rows().mapNotNull { row ->
        val at = instant(row["measured_at"]) ?: return@mapNotNull null
        val grams = row["weight_grams"].toLongOrNull()
            ?: ImportValues.decimal(row["weight_kg"])?.let { (it * GRAMS_PER_KG).roundToLong() }
            ?: return@mapNotNull null
        ImportedBodyWeight(
            id = row["id"].ifEmpty { ImportValues.stableId(ImportSource.HALTERA.name, "bw", at.toString()) },
            measuredAt = at,
            weightGrams = grams,
            notes = row["notes"],
        )
    }.toList()
}
