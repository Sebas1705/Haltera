package es.sebas1705.haltera.data.repository.share

import es.sebas1705.haltera.data.models.RoutineOrigin
import kotlin.io.encoding.Base64

/** Deflates bytes in the zlib format (RFC 1950). */
internal expect fun deflateBytes(data: ByteArray): ByteArray

/** Inflates zlib bytes; throws on corrupt input. */
internal expect fun inflateBytes(data: ByteArray): ByteArray

/**
 * The QR payload of a shared routine (spec §10.1). Three forms, all read case-insensitively by prefix. The app was
 * called Ironlog before; codes made by it (`IRONLOG:R2:`, `IRONLOG:T1:`, `ironlog:r1:`) are still read, never written.
 * - `HALTERA:R2:` + base45(deflate(json)), written by default. Every character is in the QR alphanumeric
 *   charset, so the QR uses alphanumeric mode (5.5 bits per character) and comes out less dense.
 * - `HALTERA:T1:<TEMPLATE-ID>:<version>`, for an unmodified copy of a bundled catalogue template. Template
 *   ids are lowercase `[a-z0-9-]`, written uppercase to stay alphanumeric and lowercased when read.
 * - `ironlog:r1:` + base64url(deflate(json)), the first format; still read, written only by [encodeV1].
 */
object RoutineShareCode {
    const val PREFIX = "HALTERA:R2:"
    const val PREFIX_V1 = "ironlog:r1:"
    const val TEMPLATE_PREFIX = "HALTERA:T1:"

    /** Prefixes written by the app under its former name; read only. */
    const val LEGACY_PREFIX = "IRONLOG:R2:"
    const val LEGACY_TEMPLATE_PREFIX = "IRONLOG:T1:"

    /** Longest payload we put in a QR code that still scans reliably. */
    const val MAX_QR_LENGTH = 2_000

    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
    private val templateId = Regex("[a-z0-9-]+")
    private val prefixes = listOf(PREFIX, PREFIX_V1, TEMPLATE_PREFIX, LEGACY_PREFIX, LEGACY_TEMPLATE_PREFIX)
    private val templatePrefixes = listOf(TEMPLATE_PREFIX, LEGACY_TEMPLATE_PREFIX)

    /** The v2 code of [json]. */
    fun encode(json: String): String = PREFIX + Base45.encode(deflateBytes(json.encodeToByteArray()))

    /** The legacy v1 code of [json]. */
    fun encodeV1(json: String): String = PREFIX_V1 + base64.encode(deflateBytes(json.encodeToByteArray()))

    /** The code of an unmodified copy of the catalogue template [templateId] at [version]. */
    fun encodeTemplate(templateId: String, version: Int): String {
        require(this.templateId.matches(templateId)) { "template id outside [a-z0-9-]: $templateId" }
        return "$TEMPLATE_PREFIX${templateId.uppercase()}:$version"
    }

    fun isCode(text: String): Boolean = prefixes.any { text.trim().startsWith(it, ignoreCase = true) }

    fun isTemplateCode(text: String): Boolean = templatePrefixes.any { text.trim().startsWith(it, ignoreCase = true) }

    /** The JSON in a v1 or v2 [code], or null when it is not a valid payload (template codes hold no JSON). */
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // Any decoding failure means "not a code".
    fun decode(code: String): String? {
        // Only line breaks and tabs are trimmed at the end: a base45 payload may end in a space.
        val text = code.trimStart().trimEnd('\n', '\r', '\t')
        return try {
            when {
                text.startsWith(PREFIX, ignoreCase = true) -> inflateBytes(Base45.decode(text.substring(PREFIX.length)))
                text.startsWith(LEGACY_PREFIX, ignoreCase = true) ->
                    inflateBytes(Base45.decode(text.substring(LEGACY_PREFIX.length)))
                text.startsWith(
                    PREFIX_V1,
                    ignoreCase = true
                ) -> inflateBytes(base64.decode(text.substring(PREFIX_V1.length)))
                else -> return null
            }.decodeToString()
        } catch (e: Exception) {
            null
        }
    }

    /** The template a template [code] points to, or null when it is not a valid template code. */
    fun decodeTemplate(code: String): RoutineOrigin? {
        val text = code.trim()
        val prefix = templatePrefixes.firstOrNull { text.startsWith(it, ignoreCase = true) } ?: return null
        val parts = text.substring(prefix.length).split(':')
        if (parts.size != 2) return null
        val id = parts[0].lowercase()
        val version = parts[1].toIntOrNull()
        return if (templateId.matches(id) && version != null && version >= 1) RoutineOrigin(id, version) else null
    }
}
