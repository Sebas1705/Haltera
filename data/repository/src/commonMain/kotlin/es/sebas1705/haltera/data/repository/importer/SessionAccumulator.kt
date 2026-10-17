package es.sebas1705.haltera.data.repository.importer

import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.Muscle
import es.sebas1705.haltera.data.models.TrackingType

/** Collects the rows of a file into sessions, exercises and sets, keeping the order of the file. */
internal class SessionAccumulator(private val source: ImportSource) {

    class ExerciseAcc(val name: String) {
        val notes = LinkedHashSet<String>()
        val sets = ArrayList<ImportedSet>()
        var exerciseId: String? = null
        var variantId: String? = null
        var category: ExerciseCategory? = null
        var trackingType: TrackingType? = null
        var muscles: List<Muscle> = emptyList()
        var equipment: Equipment? = null
        var slotId: String? = null
        var bilateral: Boolean? = null

        fun toExercise() = ImportedExercise(
            name = name,
            sets = sets,
            notes = notes.joinToString("\n"),
            exerciseId = exerciseId,
            variantId = variantId,
            category = category,
            trackingType = trackingType,
            muscles = muscles,
            equipment = equipment,
            slotId = slotId,
            bilateral = bilateral,
        )
    }

    class Group(
        /** The start as the file wrote it, normalised; part of the id. */
        val startText: String,
        val title: String?,
        val startedAt: Long,
        /** Set when the file carries its own session id (Haltera exports). */
        val fixedId: String?,
    ) {
        var endedAt: Long? = null
        var notes: String = ""
        var bodyWeightGrams: Long? = null
        var routineId: String? = null
        var routineName: String? = null
        var routineDayIndex: Int? = null
        val exercises = LinkedHashMap<String, ExerciseAcc>()

        fun exercise(key: String, name: String): ExerciseAcc = exercises.getOrPut(key) { ExerciseAcc(name) }
    }

    private val groups = LinkedHashMap<String, Group>()

    /** The group of [key], created from [create] on first sight. */
    fun group(key: String, create: () -> Group): Group = groups.getOrPut(key, create)

    fun build(): List<ImportedSession> {
        val ordered = groups.values.sortedWith(compareBy({ it.startText }, { it.title.orEmpty() }))
        val seen = HashMap<String, Int>()
        return ordered.mapNotNull { g ->
            val exercises = g.exercises.values.filter { it.sets.isNotEmpty() }.map { it.toExercise() }
            if (exercises.isEmpty()) return@mapNotNull null
            val id = g.fixedId ?: run {
                val ordinal = seen[g.startText] ?: 0
                seen[g.startText] = ordinal + 1
                ImportValues.stableId(source.name, g.startText, ordinal.toString())
            }
            ImportedSession(
                id = id,
                startedAt = g.startedAt,
                endedAt = (g.endedAt ?: g.startedAt).coerceAtLeast(g.startedAt),
                title = g.title,
                notes = g.notes,
                bodyWeightGrams = g.bodyWeightGrams,
                exercises = exercises,
                routineId = g.routineId,
                routineName = g.routineName,
                routineDayIndex = g.routineDayIndex,
            )
        }
    }
}
