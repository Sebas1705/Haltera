package es.sebas1705.haltera.data.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * JSON codec for the slot columns stored as text (target, prescription, progression). Decoding is
 * lenient: a malformed value or an unknown type (e.g. a rule added by a newer version) never throws.
 */
object ModelJson {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun encodeTarget(value: SlotTarget): String = json.encodeToString(SlotTarget.serializer(), value)

    /** Null when the stored target cannot be read; callers drop such slots. */
    fun decodeTarget(value: String): SlotTarget? = decodeOrNull(SlotTarget.serializer(), value)

    fun encodePrescription(value: Prescription): String = json.encodeToString(Prescription.serializer(), value)

    fun decodePrescription(value: String): Prescription =
        decodeOrNull(Prescription.serializer(), value) ?: DEFAULT_PRESCRIPTION

    fun encodeProgression(value: ProgressionRule): String = json.encodeToString(ProgressionRule.serializer(), value)

    /** Unknown rules fall back to [ProgressionRule.None]. */
    fun decodeProgression(value: String): ProgressionRule =
        decodeOrNull(ProgressionRule.serializer(), value) ?: ProgressionRule.None

    private fun <T> decodeOrNull(serializer: KSerializer<T>, value: String): T? = try {
        json.decodeFromString(serializer, value)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private val DEFAULT_PRESCRIPTION = Prescription.DEFAULT
}
