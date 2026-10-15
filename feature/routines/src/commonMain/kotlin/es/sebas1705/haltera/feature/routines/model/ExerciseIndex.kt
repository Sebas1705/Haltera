package es.sebas1705.haltera.feature.routines.model

import es.sebas1705.haltera.data.models.Exercise
import es.sebas1705.haltera.data.models.ExerciseVariant
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.data.repository.ExerciseRepository

/** The exercises (with their variants) referenced by a routine or template, to label and edit its slots. */
internal data class ExerciseIndex(val exercises: Map<String, Exercise> = emptyMap()) {
    private val variants: Map<String, ExerciseVariant> =
        exercises.values.flatMap { it.variants }.associateBy { it.id }

    fun variant(id: String): ExerciseVariant? = variants[id]

    fun exercise(id: String): Exercise? = exercises[id]

    fun exerciseOf(target: SlotTarget): Exercise? = when (target) {
        is SlotTarget.Fixed -> variants[target.variantId]?.let { exercises[it.exerciseId] }
        is SlotTarget.Flexible -> exercises[target.exerciseId]
    }

    /** Whether every exercise [target] needs is loaded. */
    fun knows(target: SlotTarget): Boolean = exerciseOf(target) != null

    operator fun plus(other: ExerciseIndex) = ExerciseIndex(exercises + other.exercises)
}

/**
 * The load type progression increments default from: the pinned variant, else the first allowed one,
 * else the exercise's first variant.
 */
internal fun loadTypeOf(target: SlotTarget, exercise: Exercise?): LoadType {
    val variants = exercise?.variants.orEmpty()
    val variant = when (target) {
        is SlotTarget.Fixed -> variants.firstOrNull { it.id == target.variantId }
        is SlotTarget.Flexible ->
            target.allowedVariantIds.firstNotNullOfOrNull { id -> variants.firstOrNull { it.id == id } }
                ?: variants.firstOrNull { !it.deprecated }
    }
    return variant?.loadType ?: LoadType.NONE
}

/** [known] plus the exercises of [targets] it lacks, loaded from the repository. */
internal suspend fun ExerciseRepository.indexFor(
    targets: Collection<SlotTarget>,
    known: ExerciseIndex = ExerciseIndex(),
): ExerciseIndex {
    val missing = targets.filterNot(known::knows)
    if (missing.isEmpty()) return known
    val fixedIds = missing.filterIsInstance<SlotTarget.Fixed>().map { it.variantId }.toSet()
    val exerciseIds = buildSet {
        if (fixedIds.isNotEmpty()) getVariants(fixedIds).forEach { add(it.exerciseId) }
        missing.filterIsInstance<SlotTarget.Flexible>().forEach { add(it.exerciseId) }
    } - known.exercises.keys
    val loaded = exerciseIds.mapNotNull { getExercise(it) }.associateBy { it.id }
    return known + ExerciseIndex(loaded)
}
