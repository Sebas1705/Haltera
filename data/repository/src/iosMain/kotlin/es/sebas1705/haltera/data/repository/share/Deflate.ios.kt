@file:OptIn(ExperimentalForeignApi::class)

package es.sebas1705.haltera.data.repository.share

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.zlib.Z_BUF_ERROR
import platform.zlib.Z_OK
import platform.zlib.compress2
import platform.zlib.compressBound
import platform.zlib.uLongfVar
import platform.zlib.uncompress

private const val BEST_COMPRESSION = 9
private const val MAX_GROWTH_STEPS = 12
private const val INITIAL_RATIO = 4
private const val INITIAL_EXTRA = 64

internal actual fun deflateBytes(data: ByteArray): ByteArray = memScoped {
    val bound = compressBound(data.size.convert()).toInt()
    val out = ByteArray(bound)
    val length = alloc<uLongfVar>()
    length.value = bound.convert()
    val input = if (data.isEmpty()) ByteArray(1) else data
    val status = out.usePinned { o ->
        input.usePinned { i ->
            compress2(
                o.addressOf(0).reinterpret(),
                length.ptr,
                i.addressOf(0).reinterpret(),
                data.size.convert(),
                BEST_COMPRESSION,
            )
        }
    }
    check(status == Z_OK) { "deflate failed: $status" }
    out.copyOf(length.value.toInt())
}

internal actual fun inflateBytes(data: ByteArray): ByteArray {
    require(data.isNotEmpty()) { "empty input" }
    var capacity = data.size * INITIAL_RATIO + INITIAL_EXTRA
    repeat(MAX_GROWTH_STEPS) {
        val result = memScoped {
            val out = ByteArray(capacity)
            val length = alloc<uLongfVar>()
            length.value = capacity.convert()
            val status = out.usePinned { o ->
                data.usePinned { i ->
                    uncompress(
                        o.addressOf(0).reinterpret(),
                        length.ptr,
                        i.addressOf(0).reinterpret(),
                        data.size.convert()
                    )
                }
            }
            when (status) {
                Z_OK -> out.copyOf(length.value.toInt())
                Z_BUF_ERROR -> null
                else -> throw IllegalArgumentException("inflate failed: $status")
            }
        }
        if (result != null) return result
        capacity *= 2
    }
    throw IllegalArgumentException("inflated data too large")
}
