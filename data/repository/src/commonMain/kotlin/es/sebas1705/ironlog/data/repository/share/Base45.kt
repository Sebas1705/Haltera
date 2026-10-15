package es.sebas1705.ironlog.data.repository.share

/**
 * Base45 (RFC 9285). Its alphabet is exactly the QR alphanumeric charset, so a QR code holding base45 text
 * uses alphanumeric mode (5.5 bits per character) instead of byte mode (8 bits per character).
 */
internal object Base45 {
    const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"

    private const val BASE = 45
    private const val BASE_SQUARED = BASE * BASE
    private const val BYTE_MASK = 0xFF
    private const val BYTE_BITS = 8
    private const val MAX_PAIR = 0xFFFF
    private const val CHUNK = 3

    fun encode(data: ByteArray): String = buildString((data.size + 1) / 2 * CHUNK) {
        var i = 0
        while (i + 1 < data.size) {
            val n = ((data[i].toInt() and BYTE_MASK) shl BYTE_BITS) + (data[i + 1].toInt() and BYTE_MASK)
            append(ALPHABET[n % BASE])
            append(ALPHABET[n / BASE % BASE])
            append(ALPHABET[n / BASE_SQUARED])
            i += 2
        }
        if (i < data.size) {
            val n = data[i].toInt() and BYTE_MASK
            append(ALPHABET[n % BASE])
            append(ALPHABET[n / BASE])
        }
    }

    /** Decodes [text]; throws [IllegalArgumentException] when it is not valid base45. */
    fun decode(text: String): ByteArray {
        require(text.length % CHUNK != 1) { "bad base45 length" }
        val out = ByteArray(text.length / CHUNK * 2 + (if (text.length % CHUNK == 2) 1 else 0))
        var o = 0
        var i = 0
        while (i < text.length) {
            val digits = minOf(CHUNK, text.length - i)
            var n = 0
            var weight = 1
            repeat(digits) { k ->
                val value = ALPHABET.indexOf(text[i + k])
                require(value >= 0) { "bad base45 character" }
                n += value * weight
                weight *= BASE
            }
            if (digits == CHUNK) {
                require(n <= MAX_PAIR) { "bad base45 triple" }
                out[o++] = (n shr BYTE_BITS).toByte()
                out[o++] = (n and BYTE_MASK).toByte()
            } else {
                require(n <= BYTE_MASK) { "bad base45 pair" }
                out[o++] = n.toByte()
            }
            i += digits
        }
        return out
    }
}
