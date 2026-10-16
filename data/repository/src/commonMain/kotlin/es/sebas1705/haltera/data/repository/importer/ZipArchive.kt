package es.sebas1705.haltera.data.repository.importer

private const val EOCD_SIGNATURE = 0x06054b50L
private const val CENTRAL_SIGNATURE = 0x02014b50L
private const val EOCD_SIZE = 22
private const val MAX_COMMENT = 0xFFFF
private const val CENTRAL_SIZE = 46
private const val LOCAL_SIZE = 30
private const val METHOD_STORED = 0
private const val BYTE_MASK = 0xFF
private const val BYTE_BITS = 8
private const val MAX_32 = 0xFFFFFFFFL

/**
 * Read access to a zip held in memory. It reads the entries that "Export my data" writes (stored, without
 * compression); a compressed entry is reported as [ImportError.COMPRESSED_ZIP] so the user can extract the file.
 */
@Suppress("MagicNumber", "ThrowsCount") // Field offsets of the zip headers (APPNOTE.TXT); damage is one error.
internal class ZipArchive private constructor(private val bytes: ByteArray, private val entries: Map<String, Entry>) {
    private class Entry(val method: Int, val compressedSize: Long, val localOffset: Long)

    /** The content of the entry named [name], or the entry with that file name in any folder; null if absent. */
    fun find(name: String): ByteArray? {
        val entry = entries[name] ?: entries.entries.firstOrNull { it.key.substringAfterLast('/') == name }?.value
            ?: return null
        if (entry.method != METHOD_STORED) throw ImportFormatException(ImportError.COMPRESSED_ZIP, name)
        val local = entry.localOffset.toInt()
        if (u32(local) != LOCAL_SIGNATURE) throw corrupt("local header of $name")
        val start = local + LOCAL_SIZE + u16(local + NAME_LENGTH_AT) + u16(local + EXTRA_LENGTH_AT)
        val end = start + entry.compressedSize
        if (start < 0 || end > bytes.size) throw corrupt("entry $name")
        return bytes.copyOfRange(start, end.toInt())
    }

    private fun u16(at: Int): Int = (bytes[at].toInt() and BYTE_MASK) or ((bytes[at + 1].toInt() and BYTE_MASK) shl BYTE_BITS)

    private fun u32(at: Int): Long = u16(at).toLong() or (u16(at + 2).toLong() shl (2 * BYTE_BITS))

    companion object {
        private const val LOCAL_SIGNATURE = 0x04034b50L
        private const val NAME_LENGTH_AT = 26
        private const val EXTRA_LENGTH_AT = 28

        private fun corrupt(what: String) = ImportFormatException(ImportError.CORRUPT, "damaged zip: $what")

        fun open(bytes: ByteArray): ZipArchive {
            val reader = ZipArchive(bytes, emptyMap())
            val eocd = reader.findEocd()
            val count = reader.u16(eocd + 10)
            var offset = reader.u32(eocd + 16)
            if (offset > MAX_32 || offset >= bytes.size) throw corrupt("central directory")
            val entries = LinkedHashMap<String, Entry>()
            repeat(count) {
                val at = offset.toInt()
                if (at + CENTRAL_SIZE > bytes.size || reader.u32(at) != CENTRAL_SIGNATURE) {
                    throw corrupt(
                        "directory entry"
                    )
                }
                val nameLength = reader.u16(at + 28)
                val extraLength = reader.u16(at + 30)
                val commentLength = reader.u16(at + 32)
                if (at + CENTRAL_SIZE + nameLength > bytes.size) throw corrupt("entry name")
                val name = bytes.decodeToString(at + CENTRAL_SIZE, at + CENTRAL_SIZE + nameLength)
                entries[name] = Entry(reader.u16(at + 10), reader.u32(at + 20), reader.u32(at + 42))
                offset += CENTRAL_SIZE + nameLength + extraLength + commentLength
            }
            return ZipArchive(bytes, entries)
        }
    }

    private fun findEocd(): Int {
        val first = (bytes.size - EOCD_SIZE).coerceAtLeast(-1)
        val last = (bytes.size - EOCD_SIZE - MAX_COMMENT).coerceAtLeast(0)
        for (at in first downTo last) {
            if (at >= 0 && u32(at) == EOCD_SIGNATURE) return at
        }
        throw corrupt("end of archive")
    }
}
