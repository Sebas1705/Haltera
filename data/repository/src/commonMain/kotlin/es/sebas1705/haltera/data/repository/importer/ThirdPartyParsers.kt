package es.sebas1705.haltera.data.repository.importer

import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.Muscle
import es.sebas1705.haltera.data.models.TrackingType
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.math.roundToLong

private const val METERS_PER_KM = 1000.0
private const val FITNOTES_DEFAULT_HOUR = 12

private fun ImportValues.When.epoch(zone: TimeZone, defaultHour: Int = 0): Long =
    toLocalDateTime(defaultHour).toInstant(zone).toEpochMilliseconds()

/** A set from the loose values of a third-party row, or null when the row measures nothing. */
@Suppress("LongParameterList") // One argument per measured column.
private fun looseSet(
    kind: es.sebas1705.haltera.data.models.SetKind,
    weight: Double?,
    unit: ImportWeightUnit?,
    reps: Int?,
    distanceM: Int?,
    durationS: Int?,
    rpe: Double?,
    completedAt: Long,
): ImportedSet? {
    val load = weight?.takeIf { it != 0.0 }
    val measured = (reps ?: 0) > 0 || load != null || (distanceM ?: 0) > 0 || (durationS ?: 0) > 0
    if (!measured) return null
    return ImportedSet(
        kind = kind,
        reps = reps?.takeIf { it > 0 },
        weight = load,
        weightUnit = unit,
        durationS = durationS?.takeIf { it > 0 },
        distanceM = distanceM?.takeIf { it > 0 },
        rpe = rpe,
        completedAt = completedAt,
    )
}

/** Parsers of the three third-party exports (docs/IMPORT-FORMATS.md). One row is one set. */
internal object ThirdPartyParsers {

    /** Hevy: `title,start_time,end_time,description,exercise_title,superset_id,exercise_notes,set_index,set_type,...`. */
    fun hevy(table: CsvTable, zone: TimeZone): ParsedImport {
        val acc = SessionAccumulator(ImportSource.HEVY)
        var skipped = 0
        for (row in table.rows()) {
            val start = ImportValues.whenOf(row["start_time"])
            val name = row["exercise_title"]
            if (start == null || name.isEmpty()) {
                skipped++
                continue
            }
            val startedAt = start.epoch(zone)
            val title = row["title"].ifEmpty { null }
            val startText = start.toLocalDateTime().toString()
            val group = acc.group("$startText|${title.orEmpty()}") {
                SessionAccumulator.Group(startText, title, startedAt, null)
            }
            ImportValues.whenOf(row["end_time"])?.let { group.endedAt = it.epoch(zone) }
            if (group.notes.isEmpty()) group.notes = row["description"]
            val exercise = group.exercise(ImportValues.matchKey(name), name)
            row["exercise_notes"].takeIf { it.isNotEmpty() }?.let { exercise.notes += it }
            val (weight, unit) = if (row["weight_kg"].isNotEmpty() || row["weight_lbs"].isEmpty()) {
                ImportValues.decimal(row["weight_kg"]) to ImportWeightUnit.KG
            } else {
                ImportValues.decimal(row["weight_lbs"]) to ImportWeightUnit.LB
            }
            val distanceM = ImportValues.decimal(row["distance_km"])?.let { (it * METERS_PER_KM).roundToLong().toInt() }
                ?: ImportValues.decimal(row["distance_miles"])?.let { ImportValues.meters(it, "mi") }
            val set = looseSet(
                kind = ImportValues.setKind(row["set_type"]),
                weight = weight,
                unit = unit,
                reps = ImportValues.integer(row["reps"]),
                distanceM = distanceM,
                durationS = ImportValues.integer(row["duration_seconds"]),
                rpe = ImportValues.rpe(row["rpe"]),
                completedAt = startedAt,
            )
            if (set == null) skipped++ else exercise.sets += set
        }
        return ParsedImport(ImportSource.HEVY, acc.build(), skippedRows = skipped)
    }

    /**
     * Strong: `Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Reps,Distance,Seconds,Notes,Workout Notes,RPE`
     * (comma on iOS) and the semicolon variants of Android (`Workout #`, `Duration (sec)`, `Weight (kg)`,
     * `Distance (meters)`, `Weight Unit`).
     */
    fun strong(table: CsvTable, zone: TimeZone): ParsedImport {
        val acc = SessionAccumulator(ImportSource.STRONG)
        val explicitUnit = table.has("weight unit") || table.has("weight (kg)") || table.has("weight (lb)") ||
            table.has("weight (lbs)")
        val tally = Tally()
        table.rows().forEach { strongRow(it, acc, zone, tally) }
        return ParsedImport(
            ImportSource.STRONG,
            acc.build(),
            skippedRows = tally.skipped,
            needsUnitChoice = !explicitUnit && tally.anyWeight,
        )
    }

    private class Tally {
        var skipped = 0
        var anyWeight = false
    }

    private fun strongRow(row: CsvTable.Row, acc: SessionAccumulator, zone: TimeZone, tally: Tally) {
        val start = ImportValues.whenOf(row["date"])
        val name = row["exercise name"]
        val order = row["set order"]
        val marker = order.lowercase()
        if (start == null || name.isEmpty()) {
            if (marker != "rest timer") tally.skipped++
            return
        }
        val startedAt = start.epoch(zone)
        val title = row["workout name"].ifEmpty { null }
        val startText = start.toLocalDateTime().toString()
        val group = acc.group("$startText|${title.orEmpty()}") {
            SessionAccumulator.Group(startText, title, startedAt, null)
        }
        ImportValues.durationSeconds(row["duration", "duration (sec)"])?.takeIf { it > 0 }
            ?.let { group.endedAt = startedAt + it * MILLIS_PER_SECOND }
        if (group.notes.isEmpty()) group.notes = row["workout notes"]
        val exercise = group.exercise(ImportValues.matchKey(name), name)
        row["notes"].takeIf { it.isNotEmpty() }?.let { exercise.notes += it }
        // Rest timer and note rows are not sets (a note row only carries the exercise note read above).
        if (marker == "rest timer" || marker == "note") return
        val (weight, unit) = strongWeight(row)
        if (weight != null) tally.anyWeight = true
        val set = looseSet(
            kind = ImportValues.setKind(if (marker.toIntOrNull() != null) "" else order),
            weight = weight,
            unit = unit,
            reps = ImportValues.integer(row["reps"]),
            distanceM = ImportValues.decimal(row["distance", "distance (meters)", "distance (m)"])
                ?.let { ImportValues.meters(it, "m") },
            durationS = ImportValues.integer(row["seconds"]),
            rpe = ImportValues.rpe(row["rpe"]),
            completedAt = startedAt,
        )
        if (set == null) tally.skipped++ else exercise.sets += set
    }

    private fun strongWeight(row: CsvTable.Row): Pair<Double?, ImportWeightUnit?> {
        row["weight (kg)"].takeIf { it.isNotEmpty() }?.let { return ImportValues.decimal(it) to ImportWeightUnit.KG }
        row["weight (lb)", "weight (lbs)"].takeIf { it.isNotEmpty() }
            ?.let { return ImportValues.decimal(it) to ImportWeightUnit.LB }
        return ImportValues.decimal(row["weight"]) to ImportValues.weightUnit(row["weight unit"])
    }

    /**
     * FitNotes: Android `Date,Exercise,Category,Weight (kgs),Reps,Distance,Distance Unit,Time,Comment` (newer:
     * `Weight,Weight Unit`), FitNotes 2 `Weight (kg),Weight (lbs),...,Notes,Kind`. A day is one session.
     */
    fun fitnotes(table: CsvTable, zone: TimeZone): ParsedImport {
        val acc = SessionAccumulator(ImportSource.FITNOTES)
        val hasUnit = table.has("weight unit") || table.columns.keys.any {
            it.startsWith("weight (") // weight (kgs), weight (kg), weight (lbs)
        }
        var skipped = 0
        var anyWeight = false
        for (row in table.rows()) {
            val day = ImportValues.whenOf(row["date"])
            val name = row["exercise"]
            if (day == null || name.isEmpty()) {
                skipped++
                continue
            }
            val startedAt = day.epoch(zone, FITNOTES_DEFAULT_HOUR)
            val startText = day.toLocalDateTime(FITNOTES_DEFAULT_HOUR).toString()
            val group = acc.group(startText) { SessionAccumulator.Group(startText, null, startedAt, null) }
            val exercise = group.exercise(ImportValues.matchKey(name), name)
            fitnotesHints(exercise, row["category"])
            row["comment", "notes"].takeIf { it.isNotEmpty() }?.let { exercise.notes += it }
            val (weight, unit) = fitnotesWeight(row)
            if (weight != null) anyWeight = true
            val set = looseSet(
                kind = ImportValues.setKind(row["kind"]),
                weight = weight,
                unit = unit,
                reps = ImportValues.integer(row["reps"]),
                distanceM = ImportValues.decimal(
                    row["distance"]
                )?.let { ImportValues.meters(it, row["distance unit"]) },
                durationS = ImportValues.durationSeconds(row["time"]),
                rpe = null,
                completedAt = startedAt,
            )
            if (set == null) skipped++ else exercise.sets += set
        }
        return ParsedImport(
            ImportSource.FITNOTES,
            acc.build(),
            skippedRows = skipped,
            needsUnitChoice = !hasUnit && anyWeight,
        )
    }

    private fun fitnotesWeight(row: CsvTable.Row): Pair<Double?, ImportWeightUnit?> {
        row["weight (kgs)", "weight (kg)"].takeIf { it.isNotEmpty() }
            ?.let { return ImportValues.decimal(it) to ImportWeightUnit.KG }
        row["weight (lbs)", "weight (lb)"].takeIf { it.isNotEmpty() }
            ?.let { return ImportValues.decimal(it) to ImportWeightUnit.LB }
        return ImportValues.decimal(row["weight"]) to ImportValues.weightUnit(row["weight unit"])
    }

    /** FitNotes categories: what a custom exercise created from the name starts with. */
    private fun fitnotesHints(exercise: SessionAccumulator.ExerciseAcc, category: String) {
        if (exercise.muscles.isNotEmpty() || exercise.category != null || category.isEmpty()) return
        when (ImportValues.matchKey(category)) {
            "cardio" -> {
                exercise.category = ExerciseCategory.CARDIO
                exercise.trackingType = TrackingType.DISTANCE_TIME
            }
            "chest" -> exercise.muscles = listOf(Muscle.CHEST)
            "back" -> exercise.muscles = listOf(Muscle.LATS)
            "shoulders" -> exercise.muscles = listOf(Muscle.SHOULDERS)
            "biceps" -> exercise.muscles = listOf(Muscle.BICEPS)
            "triceps" -> exercise.muscles = listOf(Muscle.TRICEPS)
            "legs" -> exercise.muscles = listOf(Muscle.QUADRICEPS)
            "abs" -> exercise.muscles = listOf(Muscle.ABDOMINALS)
            "calves" -> exercise.muscles = listOf(Muscle.CALVES)
        }
    }

    private const val MILLIS_PER_SECOND = 1000L
}
