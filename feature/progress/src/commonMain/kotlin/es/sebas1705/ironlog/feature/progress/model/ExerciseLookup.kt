package es.sebas1705.ironlog.feature.progress.model

import es.sebas1705.ironlog.data.models.Equipment
import es.sebas1705.ironlog.data.models.LoadType
import es.sebas1705.ironlog.data.models.LocalizedText
import es.sebas1705.ironlog.data.models.Muscle
import es.sebas1705.ironlog.data.models.TrackingType
import es.sebas1705.ironlog.data.repository.ExerciseRepository

/** What the progress screens need to know about a variant: names, equipment and muscles. */
internal data class VariantInfo(
    val variantId: String,
    val exerciseId: String,
    val exerciseName: LocalizedText,
    /** Optional override; null means "<exercise> (<equipment>)". */
    val variantName: LocalizedText?,
    val equipment: Equipment,
    val loadType: LoadType,
    val trackingType: TrackingType,
    val primaryMuscles: List<Muscle>,
)

/** Resolves variant ids to [VariantInfo], caching what it has already loaded. One instance per ViewModel. */
internal class ExerciseLookup(private val exercises: ExerciseRepository) {
    private val cache = mutableMapOf<String, VariantInfo>()

    /** Info for each known id; unknown ids are skipped. */
    suspend fun resolve(variantIds: Collection<String>): Map<String, VariantInfo> {
        val missing = variantIds.filter { it !in cache }.distinct()
        if (missing.isNotEmpty()) {
            val variants = exercises.getVariants(missing)
            val parentIds = variants.map { it.exerciseId }.distinct()
            val parents = parentIds.mapNotNull { exercises.getExercise(it) }.associateBy { it.id }
            variants.forEach { variant ->
                val exercise = parents[variant.exerciseId] ?: return@forEach
                cache[variant.id] = VariantInfo(
                    variantId = variant.id,
                    exerciseId = exercise.id,
                    exerciseName = exercise.name,
                    variantName = variant.name,
                    equipment = variant.equipment,
                    loadType = variant.loadType,
                    trackingType = exercise.trackingType,
                    primaryMuscles = exercise.primaryMuscles,
                )
            }
        }
        return variantIds.mapNotNull { id -> cache[id]?.let { id to it } }.toMap()
    }
}
