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
            group.endedAt = maxOf(group.endedAt ?: 0L, instant(row["ended_at"]) ?: 0L, instant(row["completed_at"]) ?: 0L)
                .takeIf { it > 0 }
            if (group.notes.isEmpty()) group.notes = row["session_notes"]
            if (group.bodyWeightGrams == null) {
                group.bodyWeightGrams = ImportValues.decimal(row["session_body_weight_kg"])
                    ?.let { (it * GRAMS_PER_KG).roundToLong() }
            }
            val variantId = row["variant_id"].ifEmpty { null }
            val exercise = group.exercise("${row["exercise_position"]}|$variantId|$name", name)
            row["exercise_notes"].takeIf { it.isNotEmpty() }?.let { exercise.notes += it }
            describe(exercise, row["exercise_id"].ifEmpty { null }, variantId, definitions)
            exercise.sets += set(row, startedAt)
        }
        return ParsedImport(ImportSource.HALTERA, acc.build(), skippedRows = skipped)
    }

    private fun set(row: CsvTable.Row, startedAt: Long) = ImportedSet(
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
