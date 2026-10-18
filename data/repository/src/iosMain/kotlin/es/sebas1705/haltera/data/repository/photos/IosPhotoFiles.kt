@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package es.sebas1705.haltera.data.repository.photos

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

/**
 * [PhotoFiles] in `Application Support/progress_photos`, marked as excluded from iCloud and device backups, so the
 * photos never leave the phone.
 */
class IosPhotoFiles(private val directoryPath: String = defaultDirectory()) : PhotoFiles {
    private val manager get() = NSFileManager.defaultManager

    override fun list(): List<String> =
        manager.contentsOfDirectoryAtPath(directoryPath, error = null)?.mapNotNull { it as? String }.orEmpty()

    override fun read(name: String): ByteArray? {
        val data = NSData.dataWithContentsOfFile(path(name)) ?: return null
        val bytes = ByteArray(data.length.toInt())
        if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        return bytes
    }

    override fun write(name: String, bytes: ByteArray) {
        ensureDirectory()
        val data = if (bytes.isEmpty()) {
            NSData()
        } else {
            bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        }
        check(data.writeToFile(path(name), atomically = true)) { "cannot write $name" }
    }

    override fun delete(name: String) {
        manager.removeItemAtPath(path(name), error = null)
    }

    override fun deleteAll() {
        manager.removeItemAtPath(directoryPath, error = null)
    }

    private fun ensureDirectory() {
        if (manager.fileExistsAtPath(directoryPath)) return
        manager.createDirectoryAtPath(
            directoryPath,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        NSURL.fileURLWithPath(directoryPath).setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    }

    private fun path(name: String): String {
        require('/' !in name && name != ".." && name != ".") { "not a file name: $name" }
        return "$directoryPath/$name"
    }

    companion object {
        fun defaultDirectory(): String {
            val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
                .firstOrNull() as? String ?: error("no Application Support directory")
            return "$base/progress_photos"
        }
    }
}
