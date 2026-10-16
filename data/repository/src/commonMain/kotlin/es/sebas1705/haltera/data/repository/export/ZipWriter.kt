package es.sebas1705.haltera.data.repository.export

private const val ZIP_VERSION = 20
private const val UTF8_FLAG = 0x0800
private const val LOCAL_SIGNATURE = 0x04034b50L
private const val CENTRAL_SIGNATURE = 0x02014b50L
private const val END_SIGNATURE = 0x06054b50L
private const val DOS_MIN_YEAR = 1980
private const val DOS_YEAR_SHIFT = 9
private const val DOS_MONTH_SHIFT = 5
private const val DOS_HOUR_SHIFT = 11
private const val DOS_MINUTE_SHIFT = 5
private const val DOS_SECOND_STEP = 2
private const val MAX_ENTRIES = 0xFFFF
private const val MAX_SIZE = 0xFFFFFFFFL

/** A moment as the zip format stores it (MS-DOS date and time, 2-second resolution, no time zone). */
internal data class DosTime(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int
) {
    val time: Int get() = (hour shl DOS_HOUR_SHIFT) or (minute shl DOS_MINUTE_SHIFT) or (second / DOS_SECOND_STEP)
    val date: Int
        get() = ((year.coerceAtLeast(DOS_MIN_YEAR) - DOS_MIN_YEAR) shl DOS_YEAR_SHIFT) or
            (month shl DOS_MONTH_SHIFT) or day
}

/**
 * Writes a zip archive to [sink], entries stored without compression (CSV and JSON of a logbook are small
 * and every reader opens stored entries). Each entry is a complete [ByteArrayOutput], so sizes and CRC are
 * known up front and no data descriptors are needed; the sink receives the bytes in chunks.
 */
internal class ZipWriter(private val sink: ByteSink, private val stamp: DosTime) {
    private class Entry(val name: ByteArray, val crc: Long, val size: Long, val offset: Long)

    private val entries = mutableListOf<Entry>()
    private var position = 0L

    fun entry(name: String, content: ByteArrayOutput) {
        require(entries.size < MAX_ENTRIES) { "too many entries" }
        require(content.size.toLong() <= MAX_SIZE) { "entry too large" }
        val nameBytes = name.encodeToByteArray()
        val entry = Entry(nameBytes, content.crc32(), content.size.toLong(), position)
        entries += entry
        val header = Le()
        header.u32(LOCAL_SIGNATURE).u16(ZIP_VERSION).u16(UTF8_FLAG).u16(0).u16(stamp.time).u16(stamp.date)
            .u32(entry.crc).u32(entry.size).u32(entry.size).u16(nameBytes.size).u16(0)
        emit(header.bytes())
        emit(nameBytes)
        content.copyTo(object : ByteSink {
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                sink.write(bytes, offset, length)
                position += length
            }
        })
    }

    fun finish() {
        val start = position
        entries.forEach { e ->
            val central = Le()
            central.u32(CENTRAL_SIGNATURE).u16(ZIP_VERSION).u16(ZIP_VERSION).u16(UTF8_FLAG).u16(0)
                .u16(stamp.time).u16(stamp.date).u32(e.crc).u32(e.size).u32(e.size)
                .u16(e.name.size).u16(0).u16(0).u16(0).u16(0).u32(0).u32(e.offset)
            emit(central.bytes())
            emit(e.name)
        }
        val size = position - start
        val end = Le()
        end.u32(END_SIGNATURE).u16(0).u16(0).u16(entries.size).u16(entries.size).u32(size).u32(start).u16(0)
        emit(end.bytes())
    }

    private fun emit(bytes: ByteArray) {
        sink.write(bytes)
        position += bytes.size
    }

    /** Little-endian field writer. */
    private class Le {
        private val out = ByteArrayOutput(HEADER_CAPACITY)

        fun u16(value: Int) = apply {
            out.write(byteArrayOf((value and BYTE).toByte(), ((value shr BYTE_BITS) and BYTE).toByte()))
        }

        fun u32(value: Long) = apply {
            u16((value and WORD).toInt())
            u16(((value shr WORD_BITS) and WORD).toInt())
        }

        fun bytes() = out.toByteArray()

        private companion object {
            const val HEADER_CAPACITY = 64
            const val BYTE = 0xFF
            const val BYTE_BITS = 8
            const val WORD = 0xFFFFL
            const val WORD_BITS = 16
        }
    }
}
