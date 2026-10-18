package es.sebas1705.haltera.data.repository.photos

import java.io.File
import java.io.IOException

/**
 * [PhotoFiles] in one directory of app-private storage (`filesDir/progress_photos` in the app). Auto Backup and device
 * transfer only include the database and the settings (`backup_rules.xml`, `data_extraction_rules.xml`), so these files
 * never leave the device.
 */
class DirectoryPhotoFiles(private val directory: File) : PhotoFiles {

    override fun list(): List<String> = directory.list()?.toList().orEmpty()

    override fun read(name: String): ByteArray? = file(name).takeIf { it.isFile }?.readBytes()

    override fun write(name: String, bytes: ByteArray) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("cannot create $directory")
        val target = file(name)
        val temp = File(directory, ".tmp-$name")
        temp.writeBytes(bytes)
        if (!temp.renameTo(target)) {
            // Windows (tests) cannot rename onto an existing file; Android can.
            target.delete()
            if (!temp.renameTo(target)) {
                temp.delete()
                throw IOException("cannot write $target")
            }
        }
    }

    override fun delete(name: String) {
        file(name).delete()
    }

    override fun deleteAll() {
        directory.deleteRecursively()
    }

    private fun file(name: String): File {
        require('/' !in name && '\\' !in name && name != ".." && name != ".") { "not a file name: $name" }
        return File(directory, name)
    }
}
