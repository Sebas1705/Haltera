package es.sebas1705.haltera.data.models

import kotlinx.serialization.Serializable

/** Text in the two UI languages. */
@Serializable
data class LocalizedText(val en: String, val es: String) {
    /** The text for [language] (an ISO 639-1 code such as "es"); English for any other language. */
    fun get(language: String): String = if (language.lowercase().startsWith("es")) es else en

    companion object {
        fun same(text: String) = LocalizedText(en = text, es = text)
    }
}

/** A list of instruction steps in the two UI languages. */
data class LocalizedSteps(val en: List<String>, val es: List<String>) {
    fun get(language: String): List<String> = if (language.lowercase().startsWith("es")) es else en

    companion object {
        val EMPTY = LocalizedSteps(emptyList(), emptyList())
    }
}

/** A base exercise, e.g. "Bench press" (spec §3.1). [variants] is empty when not loaded. */
data class Exercise(
    val id: String,
    val name: LocalizedText,
    val category: ExerciseCategory,
    val level: Level,
    val primaryMuscles: List<Muscle>,
    val secondaryMuscles: List<Muscle>,
    val instructions: LocalizedSteps,
    val trackingType: TrackingType,
    val source: ExerciseSource,
    val deprecated: Boolean = false,
    val variants: List<ExerciseVariant> = emptyList(),
    /** Creator of a USER exercise imported from a shared routine ("by <author>"); null for the user's own. */
    val author: String? = null,
    /** Id of the exercise on its creator's device, for an imported one (dedupe of later imports). */
    val originId: String? = null,
)

/** An exercise performed with specific equipment, e.g. `bench-press.barbell` (spec §3.1). */
data class ExerciseVariant(
    val id: String,
    val exerciseId: String,
    val equipment: Equipment,
    val loadType: LoadType,
    /** Optional name override; null means "<exercise> (<equipment>)". */
    val name: LocalizedText? = null,
    /** Optional instructions override; null means the exercise's. */
    val instructions: LocalizedSteps? = null,
    /** Media paths of the frames (spec §6.4). */
    val frames: List<String> = emptyList(),
    val source: ExerciseSource = ExerciseSource.CATALOG,
    val position: Int = 0,
    val deprecated: Boolean = false,
)

/** Filters for the exercise list. Null fields and a blank query do not filter. */
data class ExerciseFilter(
    val query: String = "",
    val category: ExerciseCategory? = null,
    /** Matches primary muscles only. */
    val muscle: Muscle? = null,
    /** Matches exercises with at least one active variant using this equipment. */
    val equipment: Equipment? = null,
)

/** Input for a user-created exercise with its variants. */
data class NewExercise(
    val name: LocalizedText,
    val category: ExerciseCategory,
    val trackingType: TrackingType,
    val level: Level = Level.BEGINNER,
    val primaryMuscles: List<Muscle> = emptyList(),
    val secondaryMuscles: List<Muscle> = emptyList(),
    val instructions: LocalizedSteps = LocalizedSteps.EMPTY,
    /** An empty list creates a single variant with [Equipment.NONE] and [LoadType.NONE]. */
    val variants: List<NewVariant> = emptyList(),
    /** Creator, for an exercise imported from a shared routine. */
    val author: String? = null,
    /** The exercise id on the creator's device, for an exercise imported from a shared routine. */
    val originId: String? = null,
)

data class NewVariant(
    val equipment: Equipment,
    val loadType: LoadType,
    val name: LocalizedText? = null,
)

/**
 * A full edit of a USER exercise (core design §3.1.1). [variants] are the variants to keep, in order: an entry
 * with an id updates that variant, one without creates a new variant. Existing variants left out are deleted, or
 * deprecated when a session or a routine still references them, so no history or routine is orphaned.
 */
data class ExerciseEdit(
    val name: LocalizedText,
    val category: ExerciseCategory,
    val trackingType: TrackingType,
    val level: Level = Level.BEGINNER,
    val primaryMuscles: List<Muscle> = emptyList(),
    val secondaryMuscles: List<Muscle> = emptyList(),
    val instructions: LocalizedSteps = LocalizedSteps.EMPTY,
    val variants: List<VariantEdit>,
)

/** A variant in an [ExerciseEdit]; [id] null means a new variant. */
data class VariantEdit(
    val id: String? = null,
    val equipment: Equipment,
    val loadType: LoadType,
    val name: LocalizedText? = null,
)

/** The edit that keeps [this] exercise as it is, with its active variants (start point of an editor). */
fun Exercise.toEdit() = ExerciseEdit(
    name = name,
    category = category,
    trackingType = trackingType,
    level = level,
    primaryMuscles = primaryMuscles,
    secondaryMuscles = secondaryMuscles,
    instructions = instructions,
    variants = variants.filterNot { it.deprecated }.map { VariantEdit(it.id, it.equipment, it.loadType, it.name) },
)

/** A routine that uses an exercise, with how many of its slots do. */
data class RoutineUse(val routineId: String, val name: String, val slots: Int)

/** Where a custom exercise is used: what deleting it would touch (core design §3.1.1). */
data class ExerciseUsage(
    val routines: List<RoutineUse> = emptyList(),
    /** Sessions with the exercise in them (a session that only added it counts too). */
    val sessions: Int = 0,
    /** Completed sets logged with it. */
    val sets: Int = 0,
    /** Variants a session references: their equipment and load type are fixed and they are never deleted. */
    val variantsWithHistory: Set<String> = emptySet(),
    /** Variants a routine slot references (pinned or allowed). */
    val variantsInRoutines: Set<String> = emptySet(),
) {
    val inRoutines: Boolean get() = routines.isNotEmpty()
    val hasHistory: Boolean get() = variantsWithHistory.isNotEmpty()
}

/** What `ExerciseRepository.deleteCustom` did. */
enum class DeleteOutcome {
    /** Nothing referenced it: rows deleted, a tombstone syncs. */
    DELETED,

    /** It has history: hidden from the catalogue and pickers, history still shows it by name. */
    HIDDEN,

    /** Routines use it and the caller did not allow removing it from them: nothing changed. */
    USED_IN_ROUTINES,
}
