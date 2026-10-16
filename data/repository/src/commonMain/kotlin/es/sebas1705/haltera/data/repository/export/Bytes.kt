package es.sebas1705.haltera.data.repository.export

/** Where an export is written. Blocking: the exporter calls it on the IO dispatcher. */
interface ByteSink {
    fun write(bytes: ByteArray, offset: Int, length: Int)
}

fun ByteSink.write(bytes: ByteArray) = write(bytes, 0, bytes.size)

/** A growing in-memory [ByteSink] (one export entry, or a whole small export in a test). */
class ByteArrayOutput(initialCapacity: Int = DEFAULT_CAPACITY) : ByteSink {
    private var buffer = ByteArray(initialCapacity)

    var size: Int = 0
        private set

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        ensure(size + length)
        bytes.copyInto(buffer, size, offset, offset + length)
        size += length
    }

    fun writeText(text: String) = write(text.encodeToByteArray())

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    /** Passes the content to [target] in chunks, without copying it all. */
    fun copyTo(target: ByteSink, chunk: Int = CHUNK) {
        var offset = 0
        while (offset < size) {
            val n = minOf(chunk, size - offset)
            target.write(buffer, offset, n)
            offset += n
        }
    }

    internal fun crc32(): Long = Crc32.of(buffer, 0, size)

    private fun ensure(capacity: Int) {
        if (capacity <= buffer.size) return
        var next = buffer.size.coerceAtLeast(1)
        while (next < capacity) next = if (next > Int.MAX_VALUE / 2) Int.MAX_VALUE else next * 2
        buffer = buffer.copyOf(next)
    }

    private companion object {
        const val DEFAULT_CAPACITY = 8_192
        const val CHUNK = 65_536
    }
}

/** CRC-32 (IEEE 802.3), as the zip format needs it. */
internal object Crc32 {
    private const val POLYNOMIAL = 0xEDB88320L
    private const val MASK = 0xFFFFFFFFL
    private const val TABLE_SIZE = 256
    private const val BYTE_BITS = 8
    private const val BYTE_MASK = 0xFF

    private val table = LongArray(TABLE_SIZE) { n ->
        var c = n.toLong()
        repeat(BYTE_BITS) { c = if (c and 1L != 0L) (c ushr 1) xor POLYNOMIAL else c ushr 1 }
        c
    }

    fun of(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Long {
        var c = MASK
        for (i in offset until offset + length) {
            c = table[((c xor bytes[i].toLong()) and BYTE_MASK.toLong()).toInt()] xor (c ushr BYTE_BITS)
        }
        return c xor MASK
    }
}
