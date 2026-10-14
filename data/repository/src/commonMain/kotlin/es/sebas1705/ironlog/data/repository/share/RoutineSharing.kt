package es.sebas1705.ironlog.data.repository.share

import es.sebas1705.ironlog.data.models.Equipment
import es.sebas1705.ironlog.data.models.ExerciseCategory
import es.sebas1705.ironlog.data.models.ExerciseSource
import es.sebas1705.ironlog.data.models.Level
import es.sebas1705.ironlog.data.models.LoadType
import es.sebas1705.ironlog.data.models.LocalizedText
import es.sebas1705.ironlog.data.models.NewExercise
import es.sebas1705.ironlog.data.models.NewVariant
import es.sebas1705.ironlog.data.models.SlotTarget
import es.sebas1705.ironlog.data.models.TrackingType
import es.sebas1705.ironlog.data.repository.ExerciseRepository
import es.sebas1705.ironlog.data.repository.RoutineRepository

/** A shared routine ready to import. */
data class ImportPreview(
    val file: RoutineFile,
    /** Display names of every referenced exercise or variant id that resolves (catalogue or embedded). */
    val names: Map<String, LocalizedText>,
    /** Referenced catalogue ids (variant or exercise) not found on this device. */
    val missingIds: List<String>,
)

sealed interface ImportPreviewResult {
    data class Ready(val preview: ImportPreview) : ImportPreviewResult
    data class UnsupportedVersion(val version: Int) : ImportPreviewResult
    data class Invalid(val reason: String) : ImportPreviewResult
}

/**
 * Export and import of routines (`ironlog-routine` v1). Import always creates a NEW routine and new
 * custom exercises; nothing existing is ever overwritten.
 */
interface RoutineSharing {
    /** The routine as `ironlog-routine` JSON, or null when it does not exist. */
    suspend fun export(routineId: String): String?

    /** Reads JSON or a QR payload (`ironlog:r1:…`) and checks which catalogue ids are missing. */
    suspend fun preview(text: String): ImportPreviewResult

    /**
     * Creates the routine as a copy and returns its id. Slots aimed at missing ids are dropped when
     * [dropMissing]; otherwise each missing id becomes a custom exercise named after it.
     */
    suspend fun import(preview: ImportPreview, dropMissing: Boolean): String
}

internal class DefaultRoutineSharing(
    private val routines: RoutineRepository,
    private val exercises: ExerciseRepository,
    private val now: () -> Long,
) : RoutineSharing {

    override suspend fun export(routineId: String): String? {
        val routine = routines.getRoutine(routineId) ?: return null
        val exerciseIds = linkedSetOf<String>()
        routine.days.flatMap { it.slots }.forEach { slot ->
            when (val t = slot.target) {
                is SlotTarget.Fixed -> exercises.getVariant(t.variantId)?.let { exerciseIds += it.exerciseId }
                is SlotTarget.Flexible -> exerciseIds += t.exerciseId
            }
        }
        val custom = exerciseIds.mapNotNull { exercises.getExercise(it) }.filter { it.source == ExerciseSource.USER }
        return RoutineExport.encode(routine, custom, now())
    }

    override suspend fun preview(text: String): ImportPreviewResult {
        val json = if (RoutineShareCode.isCode(text)) {
            RoutineShareCode.decode(text) ?: return ImportPreviewResult.Invalid("bad QR payload")
        } else {
            text
        }
        val file = when (val decoded = RoutineExport.decode(json)) {
            is RoutineDecodeResult.Valid -> decoded.file
            is RoutineDecodeResult.UnsupportedVersion -> return ImportPreviewResult.UnsupportedVersion(decoded.version)
            is RoutineDecodeResult.Invalid -> return ImportPreviewResult.Invalid(decoded.reason)
        }
        val names = mutableMapOf<String, LocalizedText>()
        file.customExercises.forEach { e ->
            names[e.id] = e.name
            e.variants.forEach { v -> names[v.id] = v.name ?: e.name }
        }
        val missing = mutableListOf<String>()
        referencedIds(file).forEach { id ->
            if (id in names) return@forEach
            val variant = exercises.getVariant(id)
            val exercise = if (variant != null) exercises.getExercise(variant.exerciseId) else exercises.getExercise(id)
            if (exercise == null) missing += id else names[id] = variant?.name ?: exercise.name
        }
        return ImportPreviewResult.Ready(ImportPreview(file, names, missing.distinct()))
    }

    override suspend fun import(preview: ImportPreview, dropMissing: Boolean): String {
        val file = preview.file
        val idMap = mutableMapOf<String, String>()
        file.customExercises.forEach { shared ->
            val created = exercises.createCustom(shared.toNewExercise())
            idMap[shared.id] = created.id
            shared.variants.forEachIndexed { i, v -> created.variants.getOrNull(i)?.let { idMap[v.id] = it.id } }
        }
        if (!dropMissing) createPlaceholders(preview.missingIds, idMap)
        val missing = preview.missingIds.toSet()
        val routineId = routines.createRoutine(file.routine.name, file.routine.description)
        file.routine.days.forEach { day ->
            val dayId = routines.addDay(routineId, day.name, RoutineExport.dayKind(day.kind))
            day.slots.forEach { slot ->
                val target = remap(slot.target, idMap, missing, dropMissing)
                if (target != null) routines.addSlot(dayId, slot.toSpec(target))
            }
        }
        return routineId
    }

    /** One custom exercise per missing exercise, named after the id, with one variant per missing variant. */
    private suspend fun createPlaceholders(missingIds: List<String>, idMap: MutableMap<String, String>) {
        // A variant id is `<exerciseId>.<equipment>`; a bare id is an exercise.
        val groups = missingIds.groupBy { id -> if ('.' in id) id.substringBeforeLast('.') else id }
        groups.forEach { (exerciseId, ids) ->
            val variantIds = ids.filter { it != exerciseId }
            val variants = variantIds.map { id ->
                val equipment = equipmentOf(id.substringAfterLast('.'))
                NewVariant(equipment, loadTypeOf(equipment))
            }
            val created = exercises.createCustom(
                NewExercise(
                    name = LocalizedText.same(exerciseId),
                    category = ExerciseCategory.STRENGTH,
                    trackingType = TrackingType.WEIGHT_REPS,
                    variants = variants,
                ),
            )
            idMap[exerciseId] = created.id
            variantIds.forEachIndexed { i, id -> created.variants.getOrNull(i)?.let { idMap[id] = it.id } }
        }
    }

    private fun remap(target: SlotTarget, idMap: Map<String, String>, missing: Set<String>, drop: Boolean): SlotTarget? =
        when (target) {
            is SlotTarget.Fixed ->
                if (drop && target.variantId in missing) null else SlotTarget.Fixed(idMap[target.variantId] ?: target.variantId)
            is SlotTarget.Flexible -> {
                if (drop && target.exerciseId in missing) {
                    null
                } else {
                    val allowed = target.allowedVariantIds.filterNot { drop && it in missing }.map { idMap[it] ?: it }
                    SlotTarget.Flexible(idMap[target.exerciseId] ?: target.exerciseId, allowed)
                }
            }
        }

    private fun referencedIds(file: RoutineFile): List<String> = file.routine.days.flatMap { it.slots }.flatMap { slot ->
        when (val t = slot.target) {
            is SlotTarget.Fixed -> listOf(t.variantId)
            is SlotTarget.Flexible -> listOf(t.exerciseId) + t.allowedVariantIds
        }
    }.distinct()

    private fun SharedExercise.toNewExercise() = NewExercise(
        name = name,
        category = ExerciseCategory.fromDb(category),
        trackingType = TrackingType.fromDb(trackingType),
        level = Level.fromDb(level),
        primaryMuscles = RoutineExport.muscles(primaryMuscles),
        secondaryMuscles = RoutineExport.muscles(secondaryMuscles),
        variants = variants.map { NewVariant(Equipment.fromDb(it.equipment), LoadType.fromDb(it.loadType), it.name) },
    )

    private fun equipmentOf(text: String): Equipment =
        Equipment.entries.firstOrNull { it.name == text.uppercase().replace('-', '_') } ?: Equipment.OTHER

    private fun loadTypeOf(equipment: Equipment): LoadType = when (equipment) {
        Equipment.BARBELL, Equipment.EZ_BAR, Equipment.TRAP_BAR -> LoadType.BARBELL
        Equipment.DUMBBELL, Equipment.KETTLEBELL -> LoadType.DUMBBELL
        Equipment.MACHINE, Equipment.CABLE, Equipment.SMITH -> LoadType.MACHINE
        Equipment.BAND -> LoadType.BAND
        Equipment.BODYWEIGHT -> LoadType.BODYWEIGHT
        else -> LoadType.NONE
    }
}
