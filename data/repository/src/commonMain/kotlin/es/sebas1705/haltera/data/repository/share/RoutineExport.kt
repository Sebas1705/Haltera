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
import es.sebas1705.haltera.data.models.Routine
import es.sebas1705.haltera.data.models.RoutineSlot
import es.sebas1705.haltera.data.models.SlotSpec
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.data.models.TrackingType
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * The `haltera-routine` v1 sharing format (spec §10.1): a routine that references catalogue exercises by
 * stable id and embeds the user exercises it uses. History is never included.
 */
@Serializable
data class RoutineFile(
    val format: String = RoutineExport.FORMAT,
    val version: Int = RoutineExport.VERSION,
    val exportedAt: Long = 0,
    val routine: SharedRoutine,
    val customExercises: List<SharedExercise> = emptyList(),
)

@Serializable
data class SharedRoutine(val name: String, val description: String = "", val days: List<SharedDay> = emptyList())

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

/** A USER exercise embedded in the file, with its original ids (remapped on import). */
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
)

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
    const val VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun encode(routine: Routine, customExercises: List<Exercise>, exportedAt: Long): String =
        json.encodeToString(RoutineFile.serializer(), toFile(routine, customExercises, exportedAt))

    fun toFile(routine: Routine, customExercises: List<Exercise>, exportedAt: Long) = RoutineFile(
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
            )
        },
    )

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
