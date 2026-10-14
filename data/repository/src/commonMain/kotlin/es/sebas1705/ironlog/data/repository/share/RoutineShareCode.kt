package es.sebas1705.ironlog.data.repository.share

import kotlin.io.encoding.Base64

/** Deflates bytes in the zlib format (RFC 1950). */
internal expect fun deflateBytes(data: ByteArray): ByteArray

/** Inflates zlib bytes; throws on corrupt input. */
internal expect fun inflateBytes(data: ByteArray): ByteArray

/**
 * The QR payload of a shared routine (spec §10.1): `ironlog:r1:` + base64url(deflate(json)).
 */
object RoutineShareCode {
    const val PREFIX = "ironlog:r1:"

    /** Longest payload we put in a QR code that still scans reliably. */
    const val MAX_QR_LENGTH = 2_000

    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    fun encode(json: String): String = PREFIX + base64.encode(deflateBytes(json.encodeToByteArray()))

    fun isCode(text: String): Boolean = text.trim().startsWith(PREFIX)

    /** The JSON in [code], or null when it is not a valid payload. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // Any decoding failure means "not a code".
    fun decode(code: String): String? {
        val trimmed = code.trim()
        if (!trimmed.startsWith(PREFIX)) return null
        return try {
            inflateBytes(base64.decode(trimmed.removePrefix(PREFIX))).decodeToString()
        } catch (e: Exception) {
            null
        }
    }
}
