package es.sebas1705.ironlog.data.repository.share

/**
 * The size of the QR code a text needs at error correction level L (ISO/IEC 18004), as the QR library picks
 * it: alphanumeric mode when every character is in [Base45.ALPHABET], byte mode otherwise.
 */
object QrCapacity {
    /** Data codewords of versions 1..40 at level L. */
    private val DATA_CODEWORDS_L = intArrayOf(
        19, 34, 55, 80, 108, 136, 156, 194, 232, 274,
        324, 370, 428, 461, 523, 589, 647, 721, 795, 861,
        932, 1006, 1094, 1174, 1276, 1370, 1468, 1531, 1631, 1735,
        1843, 1955, 2071, 2191, 2306, 2434, 2566, 2702, 2812, 2956,
    )

    /** Character count bits for versions 1-9, 10-26 and 27-40. */
    private val ALNUM_COUNT_BITS = intArrayOf(9, 11, 13)
    private val BYTE_COUNT_BITS = intArrayOf(8, 16, 16)
    private const val MODE_BITS = 4
    private const val CODEWORD_BITS = 8
    private const val SMALL_VERSIONS = 9
    private const val MEDIUM_VERSIONS = 26
    private const val ALNUM_PAIR_BITS = 11
    private const val ALNUM_SINGLE_BITS = 6
    private const val MODULES_BASE = 17
    private const val MODULES_PER_VERSION = 4

    fun isAlphanumeric(text: String): Boolean = text.isNotEmpty() && text.all { it in Base45.ALPHABET }

    /** Bits of the encoded segment (mode, character count and data) in the version [version]. */
    fun dataBits(text: String, version: Int): Int {
        val tier = when {
            version <= SMALL_VERSIONS -> 0
            version <= MEDIUM_VERSIONS -> 1
            else -> 2
        }
        return if (isAlphanumeric(text)) {
            MODE_BITS + ALNUM_COUNT_BITS[tier] + text.length / 2 * ALNUM_PAIR_BITS + text.length % 2 * ALNUM_SINGLE_BITS
        } else {
            MODE_BITS + BYTE_COUNT_BITS[tier] + text.encodeToByteArray().size * CODEWORD_BITS
        }
    }

    /** The smallest QR version (1..40) that holds [text] at level L, or null when none does. */
    fun version(text: String): Int? = (1..DATA_CODEWORDS_L.size).firstOrNull { v ->
        dataBits(text, v) <= DATA_CODEWORDS_L[v - 1] * CODEWORD_BITS
    }

    /** Modules per side of a QR code of [version]. */
    fun modules(version: Int): Int = MODULES_BASE + MODULES_PER_VERSION * version
}
