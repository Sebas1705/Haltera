package es.sebas1705.haltera.data.repository

import app.cash.turbine.test
import es.sebas1705.haltera.data.repository.photos.DirectoryPhotoFiles
import es.sebas1705.haltera.data.repository.photos.FileProgressPhotoRepository
import es.sebas1705.haltera.data.repository.photos.PhotoFiles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgressPhotoRepositoryTest {
    private val root: File = Files.createTempDirectory("haltera-photos").toFile()
    private val dir = File(root, "progress_photos")
    private val clock = TestClock(1_000)
    private val image = byteArrayOf(1, 2, 3, 4)
    private val thumb = byteArrayOf(9, 9)

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun TestScope.repo(files: PhotoFiles = DirectoryPhotoFiles(dir)) =
        FileProgressPhotoRepository(files, UnconfinedTestDispatcher(testScheduler), clock)

    private fun names() = dir.list()?.sorted().orEmpty()

    @Test
    fun `no directory means no photos and nothing is created by reading`() = runTest {
        assertEquals(emptyList(), repo().observeAll().first())
        assertFalse(dir.exists())
    }

    @Test
    fun `a photo is stored as image, thumbnail and metadata named after its id`() = runTest {
        val repo = repo()
        val id = repo.add(image, thumb, takenAt = 50_000, notes = "  front ")
        assertEquals(listOf("$id.jpg", "$id.json", "$id.thumb.jpg"), names())
        val photo = repo.observeAll().first().single()
        assertEquals(id, photo.id)
        assertEquals(50_000L, photo.takenAt)
        assertEquals("front", photo.notes)
        assertEquals(1_000L, photo.addedAt)
        assertContentEquals(image, repo.image(id))
        assertContentEquals(thumb, repo.thumbnail(id))
        // No temporary files are left behind.
        assertTrue(names().none { it.startsWith(".tmp") })
    }

    @Test
    fun `photos survive a restart and are ordered by date, then by when they were added`() = runTest {
        val first = repo()
        val old = first.add(image, thumb, takenAt = 10)
        clock.millis = 2_000
        val sameDayEarlier = first.add(image, thumb, takenAt = 20)
        clock.millis = 3_000
        val sameDayLater = first.add(image, thumb, takenAt = 20)
        val reopened = repo()
        assertEquals(listOf(sameDayLater, sameDayEarlier, old), reopened.observeAll().first().map { it.id })
    }

    @Test
    fun `an empty thumbnail falls back to the image`() = runTest {
        val repo = repo()
        val id = repo.add(image, ByteArray(0), takenAt = 1)
        assertContentEquals(image, repo.thumbnail(id))
        assertFailsWith<IllegalArgumentException> { repo.add(ByteArray(0), thumb, takenAt = 1) }
    }

    @Test
    fun `editing changes the date and the note only`() = runTest {
        val repo = repo()
        val id = repo.add(image, thumb, takenAt = 10, notes = "a")
        val photo = repo.observeAll().first().single()
        repo.update(photo.copy(takenAt = 99, notes = "side ", addedAt = 123))
        val edited = repo().observeAll().first().single() // read back from disk
        assertEquals(Triple(99L, "side", 1_000L), Triple(edited.takenAt, edited.notes, edited.addedAt))
        assertContentEquals(image, repo.image(id))
        repo.update(photo.copy(id = "unknown")) // ignored
        assertEquals(1, repo.observeAll().first().size)
    }

    @Test
    fun `delete removes every file of the photo`() = runTest {
        val repo = repo()
        val keep = repo.add(image, thumb, takenAt = 1)
        val gone = repo.add(image, thumb, takenAt = 2)
        repo.observeAll().test {
            assertEquals(2, awaitItem().size)
            repo.delete(gone)
            assertEquals(listOf(keep), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(names().none { it.startsWith(gone) })
        assertNull(repo.image(gone))
        assertNull(repo.thumbnail(gone))
        repo.delete(gone) // twice is fine
    }

    @Test
    fun `delete all removes the directory and every photo`() = runTest {
        val repo = repo()
        repeat(3) { repo.add(image, thumb, takenAt = it.toLong()) }
        repo.deleteAll()
        assertFalse(dir.exists())
        assertEquals(emptyList(), repo.observeAll().first())
        assertEquals(emptyList(), repo().observeAll().first())
        // The store keeps working afterwards.
        repo.add(image, thumb, takenAt = 1)
        assertEquals(1, repo.observeAll().first().size)
    }

    @Test
    fun `interrupted adds, missing images, bad metadata and temporary files are cleaned up on load`() = runTest {
        val repo = repo()
        val good = repo.add(image, thumb, takenAt = 1)
        File(dir, "orphan.jpg").writeBytes(image) // the app died before writing the metadata
        File(dir, "orphan.thumb.jpg").writeBytes(thumb)
        File(dir, "noimage.json").writeText("""{"id":"noimage","takenAt":1}""") // image deleted outside the app
        File(dir, "broken.json").writeText("not json")
        File(dir, "broken.jpg").writeBytes(image)
        File(dir, "other.json").writeText("""{"id":"someone-else","takenAt":1}""") // id does not match its name
        File(dir, "other.jpg").writeBytes(image)
        File(dir, ".tmp-$good.json").writeText("half")

        assertEquals(listOf(good), repo().observeAll().first().map { it.id })
        assertEquals(listOf("$good.jpg", "$good.json", "$good.thumb.jpg"), names())
    }

    @Test
    fun `ids that are not uuids never reach the file system`() = runTest {
        val repo = repo()
        File(root, "secret.jpg").writeBytes(image)
        assertNull(repo.image("../secret"))
        assertNull(repo.thumbnail("../secret"))
        repo.delete("../secret")
        assertTrue(File(root, "secret.jpg").exists())
        assertFailsWith<IllegalArgumentException> { DirectoryPhotoFiles(dir).read("../secret.jpg") }
    }

    @Test
    fun `a failed write leaves nothing behind`() = runTest {
        val failing = object : PhotoFiles by DirectoryPhotoFiles(dir) {
            override fun write(name: String, bytes: ByteArray) {
                if (name.endsWith(".json")) throw IOException("disk full")
                DirectoryPhotoFiles(dir).write(name, bytes)
            }
        }
        val repo = repo(failing)
        assertFailsWith<IOException> { repo.add(image, thumb, takenAt = 1) }
        assertEquals(emptyList(), names())
        assertEquals(emptyList(), repo.observeAll().first())
    }
}
