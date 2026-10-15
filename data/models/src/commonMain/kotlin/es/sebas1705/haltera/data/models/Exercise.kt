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
)

data class NewVariant(
    val equipment: Equipment,
    val loadType: LoadType,
    val name: LocalizedText? = null,
)
