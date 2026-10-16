package es.sebas1705.haltera.data.repository.share

import es.sebas1705.haltera.data.models.DayKind
import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.Exercise
import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.Level
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.LocalizedText
import es.sebas1705.haltera.data.models.Muscle
import es.sebas1705.haltera.data.models.Prescription
import es.sebas1705.haltera.data.models.ProgressionRule
import es.sebas1705.haltera.data.models.RestMode
import es.sebas1705.haltera.data.models.RestPolicy
import es.sebas1705.haltera.data.models.Routine
import es.sebas1705.haltera.data.models.RoutineSlot
import es.sebas1705.haltera.data.models.SlotSpec
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.data.models.TrackingType
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * The `haltera-routine` sharing format (spec §10.1): a routine that references catalogue exercises by
 * stable id and embeds the user exercises it uses. History is never included. Version 2 adds the custom
 * exercises' instructions, creator name and origin id; a file is written as version 1 when it embeds no custom
 * exercise, so older apps still read it. Version 1 files (and the legacy `ironlog-routine` key) still import.
 */
@Serializable
data class RoutineFile(
    val format: String = RoutineExport.FORMAT,
    val version: Int = RoutineExport.VERSION,
    val exportedAt: Long = 0,
    val routine: SharedRoutine,
    val customExercises: List<SharedExercise> = emptyList(),
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SharedRoutine(
    val name: String,
    val description: String = "",
    val days: List<SharedDay> = emptyList(),
    /**
     * Flexible rest days (docs/designs/2026-10-16-flexible-rest-days.md). Written only for flexible routines, so a
     * fixed routine encodes exactly as before; absent (older codes) means fixed. Older apps ignore it.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val rest: SharedRest? = null,
)

/** The rest policy of a flexible routine: [weekdays] are ISO numbers (1 = Monday); empty = not known. */
@Serializable
data class SharedRest(
    val mode: String = RestMode.FLEXIBLE.name,
    val daysPerWeek: Int = 0,
    val weekdays: List<Int> = emptyList(),
) {
    /** The policy this describes; an unknown [mode] reads as fixed. */
    fun toPolicy(): RestPolicy = when (RestMode.fromDb(mode)) {
        RestMode.FIXED -> RestPolicy.FIXED
        RestMode.FLEXIBLE ->
            if (weekdays.isNotEmpty()) RestPolicy.onWeekdays(weekdays) else RestPolicy.flexible(daysPerWeek)
    }

    companion object {
        /** Null for a fixed routine (nothing is written). */
        fun of(policy: RestPolicy): SharedRest? {
            val p = policy.normalized()
            return if (!p.isFlexible) null else SharedRest(RestMode.FLEXIBLE.name, p.restDaysCount, p.weekdays)
        }
    }
}

@Serializable
data class SharedDay(
    val name: String,
    val kind: String = DayKind.TRAINING.name,
    val slots: List<SharedSlot> = emptyList()
)

@Serializable
data class SharedSlot(
    val target: SlotTarget,
    val sets: Int = 3,
    val warmupSets: Int = 0,
    val prescription: Prescription = Prescription.DEFAULT,
    val restBetweenSets: Int = RoutineSlot.DEFAULT_REST_S,
    val restAfter: Int = RoutineSlot.DEFAULT_REST_S,
    val supersetGroup: Int? = null,
    val bilateral: Boolean = false,
    val notes: String = "",
    val progression: ProgressionRule = ProgressionRule.None,
) {
    fun toSpec(target: SlotTarget = this.target) = SlotSpec(
        target, sets, warmupSets, prescription, restBetweenSets, restAfter, supersetGroup, bilateral, notes, progression,
    )
}

/**
 * A USER exercise embedded in the file, with its original ids (remapped on import). The fields after [variants]
 * came with version 2 and are left out of the JSON when unset, so a file without them reads as version 1 did.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SharedExercise(
    val id: String,
    val name: LocalizedText,
    val category: String = ExerciseCategory.STRENGTH.name,
    val level: String = Level.BEGINNER.name,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val trackingType: String = TrackingType.WEIGHT_REPS.name,
    val variants: List<SharedVariant> = emptyList(),
    /** v2: instruction steps in both languages. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val instructions: SharedSteps? = null,
    /** v2: the creator's name ("Your name for shared routines"); null when they left it empty. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val author: String? = null,
    /**
     * v2: stable id of the exercise on its creator's device, kept when it is re-shared, so importing the same
     * exercise twice reuses the first copy. Version 1 files only have [id], which is just as unique (`user-<uuid>`).
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val originId: String? = null,
) {
    /** The id that identifies this exercise across devices. */
    val origin: String get() = originId ?: id
}

/** Instruction steps in both languages (v2). */
@Serializable
data class SharedSteps(val en: List<String> = emptyList(), val es: List<String> = emptyList())

@Serializable
data class SharedVariant(
    val id: String,
    val equipment: String = Equipment.NONE.name,
    val loadType: String = LoadType.NONE.name,
    val name: LocalizedText? = null,
)

/** Result of reading a shared routine. */
sealed interface RoutineDecodeResult {
    data class Valid(val file: RoutineFile) : RoutineDecodeResult

    /** Written by a newer app version. */
    data class UnsupportedVersion(val version: Int) : RoutineDecodeResult

    data class Invalid(val reason: String) : RoutineDecodeResult
}

/** JSON codec of [RoutineFile]. */
object RoutineExport {
    const val FORMAT = "haltera-routine"

    /** The format key written while the app was called Ironlog; still read, never written. */
    const val LEGACY_FORMAT = "ironlog-routine"

    /** The newest version this app reads; written only when the file embeds custom exercises (v2 fields). */
    const val VERSION = 2

    /** Written when nothing needs v2, so older app versions still import routines without custom exercises. */
    const val VERSION_1 = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    /**
     * [author] is the sharer's name: it is written on the custom exercises they created themselves, while an
     * exercise imported from someone else keeps its creator. Blank means no name.
     */
    fun encode(routine: Routine, customExercises: List<Exercise>, exportedAt: Long, author: String? = null): String =
        json.encodeToString(RoutineFile.serializer(), toFile(routine, customExercises, exportedAt, author))

    fun toFile(
        routine: Routine,
        customExercises: List<Exercise>,
        exportedAt: Long,
        author: String? = null,
    ) = RoutineFile(
        version = if (customExercises.isEmpty()) VERSION_1 else VERSION,
        exportedAt = exportedAt,
        routine = SharedRoutine(
            name = routine.name,
            description = routine.description,
            days = routine.days.sortedBy { it.position }.map { day ->
                SharedDay(
                    name = day.name,
                    kind = day.kind.name,
                    slots = day.slots.sortedBy { it.position }.map { s ->
                        SharedSlot(
                            s.target, s.sets, s.warmupSets, s.prescription, s.restBetweenSetsS, s.restAfterS,
                            s.supersetGroup, s.bilateral, s.notes, s.progression,
                        )
                    },
                )
            },
            rest = SharedRest.of(routine.restPolicy),
        ),
        customExercises = customExercises.map { e ->
            SharedExercise(
                id = e.id,
                name = e.name,
                category = e.category.name,
                level = e.level.name,
                primaryMuscles = e.primaryMuscles.map { it.name },
                secondaryMuscles = e.secondaryMuscles.map { it.name },
                trackingType = e.trackingType.name,
                variants = e.variants.map { v -> SharedVariant(v.id, v.equipment.name, v.loadType.name, v.name) },
                instructions = e.instructions.takeIf { it.en.isNotEmpty() || it.es.isNotEmpty() }
                    ?.let { SharedSteps(it.en, it.es) },
                author = e.author ?: author?.trim()?.takeIf { it.isNotEmpty() },
                originId = e.originId ?: e.id,
            )
        },
    )

    /** [file] without the custom exercises' instructions: the smaller payload tried when a QR code overflows. */
    fun withoutInstructions(file: RoutineFile) =
        file.copy(customExercises = file.customExercises.map { it.copy(instructions = null) })

    fun encode(file: RoutineFile): String = json.encodeToString(RoutineFile.serializer(), file)

    /** Reads [text]; never throws. */
    @Suppress("ReturnCount") // One early return per kind of bad input reads best.
    fun decode(text: String): RoutineDecodeResult {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (_: SerializationException) {
            return RoutineDecodeResult.Invalid("not JSON")
        } catch (_: IllegalArgumentException) {
            return RoutineDecodeResult.Invalid("not a JSON object")
        }
        val format = (root["format"] as? JsonPrimitive)?.content
        if (format != FORMAT && format != LEGACY_FORMAT) return RoutineDecodeResult.Invalid("unknown format")
        val version = (root["version"] as? JsonPrimitive)?.intOrNull
            ?: return RoutineDecodeResult.Invalid("missing version")
        if (version > VERSION) return RoutineDecodeResult.UnsupportedVersion(version)
        if (version < 1) return RoutineDecodeResult.Invalid("bad version")
        return try {
            RoutineDecodeResult.Valid(json.decodeFromJsonElement(RoutineFile.serializer(), root))
        } catch (e: SerializationException) {
            RoutineDecodeResult.Invalid(e.message ?: "bad content")
        } catch (e: IllegalArgumentException) {
            RoutineDecodeResult.Invalid(e.message ?: "bad content")
        }
    }

    internal fun dayKind(value: String) = DayKind.fromDb(value)

    internal fun muscles(values: List<String>) = values.mapNotNull { v -> Muscle.entries.firstOrNull { it.name == v } }
}
