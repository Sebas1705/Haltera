package es.sebas1705.haltera.data.repository.share

import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.Exercise
import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.ExerciseSource
import es.sebas1705.haltera.data.models.ExerciseVariant
import es.sebas1705.haltera.data.models.Level
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.LocalizedSteps
import es.sebas1705.haltera.data.models.LocalizedText
import es.sebas1705.haltera.data.models.NewExercise
import es.sebas1705.haltera.data.models.NewVariant
import es.sebas1705.haltera.data.models.Routine
import es.sebas1705.haltera.data.models.RoutineOrigin
import es.sebas1705.haltera.data.models.RoutineTemplate
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.data.models.VariantEdit
import es.sebas1705.haltera.data.models.toEdit
import es.sebas1705.haltera.data.repository.ExerciseRepository
import es.sebas1705.haltera.data.repository.RoutineRepository

/** A shared routine ready to import. */
data class ImportPreview(
    val file: RoutineFile,
    /** Display names of every referenced exercise or variant id that resolves (catalogue or embedded). */
    val names: Map<String, LocalizedText>,
    /** Referenced catalogue ids (variant or exercise) not found on this device. */
    val missingIds: List<String>,
    /** Set when the code named an unmodified catalogue template: import copies it from the catalogue. */
    val template: RoutineOrigin? = null,
    /** The language [file] was materialised in when it comes from [template]. */
    val language: String = "en",
    /** Ids (in [file]) of embedded custom exercises already on this device: import reuses them. */
    val existingCustomIds: Set<String> = emptySet(),
) {
    /** Distinct creators named by the embedded custom exercises. */
    val authors: List<String> get() = file.customExercises.mapNotNull { it.author }.distinct()
}

sealed interface ImportPreviewResult {
    data class Ready(val preview: ImportPreview) : ImportPreviewResult
    data class UnsupportedVersion(val version: Int) : ImportPreviewResult
    data class Invalid(val reason: String) : ImportPreviewResult
}

/**
 * Export and import of routines (`haltera-routine` v1). Import always creates a NEW routine and new
 * custom exercises; nothing existing is ever overwritten.
 */
interface RoutineSharing {
    /**
     * The routine as `haltera-routine` JSON, or null when it does not exist. [author] (the user's "name for shared
     * routines") is written as the creator of the custom exercises they made; null or blank writes no name.
     */
    suspend fun export(routineId: String, author: String? = null): String?

    /**
     * The QR payload of the routine (see [RoutineShareCode]): a template code for an unmodified catalogue
     * copy, otherwise the code of [export]. When that is longer than [RoutineShareCode.MAX_QR_LENGTH] and custom
     * exercises carry instructions, the code without those instructions is returned if it fits (the file share
     * keeps everything). Null when the routine does not exist.
     */
    suspend fun shareCode(routineId: String, author: String? = null): String? =
        export(routineId, author)?.let(RoutineShareCode::encode)

    /**
     * Reads JSON or a QR payload (any [RoutineShareCode] form) and checks which catalogue ids are missing. A
     * template code is materialised in [language], as a catalogue copy would be.
     */
    suspend fun preview(text: String, language: String = "en"): ImportPreviewResult

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

    override suspend fun export(routineId: String, author: String?): String? =
        exportFile(routineId, author)?.let(RoutineExport::encode)

    private suspend fun exportFile(routineId: String, author: String?): RoutineFile? {
        val routine = routines.getRoutine(routineId) ?: return null
        val exerciseIds = linkedSetOf<String>()
        routine.days.flatMap { it.slots }.forEach { slot ->
            when (val t = slot.target) {
                is SlotTarget.Fixed -> exercises.getVariant(t.variantId)?.let { exerciseIds += it.exerciseId }
                is SlotTarget.Flexible -> exerciseIds += t.exerciseId
            }
        }
        val custom = exerciseIds.mapNotNull { exercises.getExercise(it) }.filter { it.source == ExerciseSource.USER }
        return RoutineExport.toFile(routine, custom, now(), author)
    }

    override suspend fun shareCode(routineId: String, author: String?): String? {
        val routine = routines.getRoutine(routineId) ?: return null
        val template = routine.origin?.let { origin ->
            routines.templates().firstOrNull { it.id == origin.templateId && it.version == origin.version }
        }
        if (template != null && isUnmodifiedCopy(routine, template)) {
            return RoutineShareCode.encodeTemplate(template.id, template.version)
        }
        val file = exportFile(routineId, author) ?: return null
        val code = RoutineShareCode.encode(RoutineExport.encode(file))
        if (code.length <= RoutineShareCode.MAX_QR_LENGTH) return code
        // Instructions are the bulk of custom exercises: a QR without them still carries the routine.
        val lite = RoutineShareCode.encode(RoutineExport.encode(RoutineExport.withoutInstructions(file)))
        return if (lite.length < code.length) lite else code
    }

    /** Whether [routine] has exactly the content [template] has in English or in Spanish. */
    private fun isUnmodifiedCopy(routine: Routine, template: RoutineTemplate): Boolean {
        val content = RoutineExport.toFile(routine, emptyList(), 0).routine
        return LANGUAGES.any { content == materialise(template, it) }
    }

    override suspend fun preview(text: String, language: String): ImportPreviewResult {
        if (RoutineShareCode.isTemplateCode(text)) return previewTemplate(text, language)
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
        return ImportPreviewResult.Ready(previewOf(file))
    }

    private suspend fun previewTemplate(text: String, language: String): ImportPreviewResult {
        val origin = RoutineShareCode.decodeTemplate(text) ?: return ImportPreviewResult.Invalid("bad template code")
        val template = routines.templates().firstOrNull { it.id == origin.templateId }
            ?: return ImportPreviewResult.Invalid("unknown template ${origin.templateId}")
        // A newer revision than the bundled one needs a newer app; an older one imports the bundled revision.
        if (origin.version > template.version) return ImportPreviewResult.UnsupportedVersion(origin.version)
        val file = RoutineFile(routine = materialise(template, language))
        return ImportPreviewResult.Ready(
            previewOf(file).copy(template = RoutineOrigin(template.id, template.version), language = language),
        )
    }

    private suspend fun previewOf(file: RoutineFile): ImportPreview {
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
        val existing = file.customExercises.filter { exercises.findByOrigin(it.origin) != null }.map { it.id }.toSet()
        return ImportPreview(file, names, missing.distinct(), existingCustomIds = existing)
    }

    override suspend fun import(preview: ImportPreview, dropMissing: Boolean): String {
        // A catalogue template is copied as the catalogue screen does; its exercises ship with the app.
        preview.template?.let { return routines.copyTemplate(it.templateId, preview.language) }
        val file = preview.file
        val idMap = mutableMapOf<String, String>()
        file.customExercises.forEach { shared ->
            val existing = exercises.findByOrigin(shared.origin)
            if (existing == null) {
                val created = exercises.createCustom(shared.toNewExercise())
                idMap[shared.id] = created.id
                shared.variants.forEachIndexed { i, v -> created.variants.getOrNull(i)?.let { idMap[v.id] = it.id } }
            } else {
                exercises.reuse(existing, shared, idMap)
            }
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
        // Codes without `rest` (older apps, fixed routines) stay fixed.
        file.routine.rest?.toPolicy()?.takeIf { it.isFlexible }?.let { routines.setRestPolicy(routineId, it) }
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
                if (drop && target.variantId in missing) {
                    null
                } else {
                    SlotTarget.Fixed(
                        idMap[target.variantId] ?: target.variantId
                    )
                }
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
        instructions = instructions?.let { LocalizedSteps(it.en, it.es) } ?: LocalizedSteps.EMPTY,
        author = author?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_AUTHOR_LENGTH),
        originId = origin,
    )

    /** [template] as a shared routine with its texts in [language], as [RoutineRepository.copyTemplate] stores it. */
    private fun materialise(template: RoutineTemplate, language: String) = SharedRoutine(
        name = template.name.get(language),
        description = template.description.get(language),
        days = template.days.map { day ->
            SharedDay(
                name = day.name.get(language),
                kind = day.kind.name,
                slots = day.slots.map { s ->
                    SharedSlot(
                        s.target, s.sets, s.warmupSets, s.prescription, s.restBetweenSetsS, s.restAfterS,
                        s.supersetGroup, s.bilateral, s.notes, s.progression,
                    )
                },
            )
        },
    )

    private companion object {
        /** The languages a catalogue copy can be stored in. */
        val LANGUAGES = listOf("en", "es")

        /** A creator name longer than this is cut (it comes from a file anyone can write). */
        const val MAX_AUTHOR_LENGTH = 60
    }
}

/**
 * Maps [shared] onto [existing], the copy imported earlier (or the user's own exercise): it is restored if it
 * was soft-deleted, and never overwritten, since the user may have edited it. Each shared variant maps to the
 * same id (own exercise), else an active variant with the same equipment and load type, else the same
 * equipment; a variant with no match is added to the existing exercise.
 */
private suspend fun ExerciseRepository.reuse(
    existing: Exercise,
    shared: SharedExercise,
    idMap: MutableMap<String, String>,
) {
    if (existing.deprecated) restoreCustom(existing.id)
    idMap[shared.id] = existing.id
    fun match(e: Exercise, v: SharedVariant): ExerciseVariant? {
        val equipment = Equipment.fromDb(v.equipment)
        val active = e.variants.filterNot { it.deprecated }
        return e.variants.firstOrNull { it.id == v.id }
            ?: active.firstOrNull { it.equipment == equipment && it.loadType == LoadType.fromDb(v.loadType) }
            ?: active.firstOrNull { it.equipment == equipment }
    }
    val unmatched = shared.variants.filter { match(existing, it) == null }
    val target = if (unmatched.isEmpty()) {
        existing
    } else {
        val edit = existing.toEdit()
        updateCustom(
            existing.id,
            edit.copy(
                variants = edit.variants + unmatched.map {
                    VariantEdit(null, Equipment.fromDb(it.equipment), LoadType.fromDb(it.loadType), it.name)
                },
            ),
        )
    }
    shared.variants.forEach { v -> match(target, v)?.let { idMap[v.id] = it.id } }
}

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
