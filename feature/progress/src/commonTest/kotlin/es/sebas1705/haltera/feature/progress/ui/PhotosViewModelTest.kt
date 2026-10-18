package es.sebas1705.haltera.feature.progress.ui

import app.cash.turbine.test
import es.sebas1705.haltera.data.models.ProgressPhoto
import es.sebas1705.haltera.feature.progress.MainDispatcherTest
import es.sebas1705.haltera.feature.progress.NOW
import es.sebas1705.haltera.feature.progress.fakes.FakePhotoPicker
import es.sebas1705.haltera.feature.progress.fakes.FakeProgressPhotoRepository
import es.sebas1705.haltera.feature.progress.photos.NoProgressPhotoPicker
import es.sebas1705.haltera.feature.progress.photos.PickedPhoto
import es.sebas1705.haltera.feature.progress.testClock
import es.sebas1705.haltera.feature.progress.ui.photos.CompareSide
import es.sebas1705.haltera.feature.progress.ui.photos.PhotoError
import es.sebas1705.haltera.feature.progress.ui.photos.PhotosIntent
import es.sebas1705.haltera.feature.progress.ui.photos.PhotosViewModel
import es.sebas1705.haltera.feature.progress.utc
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotosViewModelTest : MainDispatcherTest() {
    private val image = byteArrayOf(1, 2, 3)
    private val thumb = byteArrayOf(4)

    private val three = listOf(
        ProgressPhoto("old", utc(2026, 8, 1, hour = 9), addedAt = 1),
        ProgressPhoto("mid", utc(2026, 9, 1, hour = 9), addedAt = 2),
        ProgressPhoto("new", utc(2026, 10, 10, hour = 9), addedAt = 3),
    )

    @Test
    fun `without a picker adding is hidden and an empty store shows no photos`() = runTest(dispatcher) {
        val vm = PhotosViewModel(FakeProgressPhotoRepository(), NoProgressPhotoPicker, testClock())
        vm.uiState.test {
            val state = awaitItem()
            assertFalse(state.loading)
            assertFalse(state.pickerAvailable)
            assertTrue(state.photos.isEmpty())
            assertFalse(state.canCompare)
            vm.onEvent(PhotosIntent.Add)
            assertNull(vm.uiState.value.pending)
        }
    }

    @Test
    fun `a picked photo is dated when it was taken and stored with its note`() = runTest(dispatcher) {
        val taken = utc(2026, 10, 3, hour = 8, minute = 15)
        val repo = FakeProgressPhotoRepository()
        val vm = PhotosViewModel(repo, FakePhotoPicker(PickedPhoto(image, thumb, taken)), testClock())
        vm.uiState.test {
            awaitItem()
            vm.onEvent(PhotosIntent.Add)
            val pending = assertNotNull(expectMostRecentItem().pending)
            assertEquals(utc(2026, 10, 3, hour = 0), pending.dateUtcMillis)
            vm.onEvent(PhotosIntent.PendingNotesChanged(" front, relaxed "))
            vm.onEvent(PhotosIntent.SavePending)
            val state = expectMostRecentItem()
            assertNull(state.pending)
            val photo = state.photos.single()
            assertEquals(taken, photo.takenAt) // the exact moment is kept
            assertEquals("front, relaxed", photo.notes)
            assertContentEquals(image, repo.images.getValue(photo.id))
        }
    }

    @Test
    fun `a photo without a date, or dated in the future, is dated today and saved now`() = runTest(dispatcher) {
        for (takenAt in listOf(null, NOW + 3 * 86_400_000L)) {
            val repo = FakeProgressPhotoRepository()
            val vm = PhotosViewModel(repo, FakePhotoPicker(PickedPhoto(image, thumb, takenAt)), testClock())
            vm.uiState.test {
                awaitItem()
                vm.onEvent(PhotosIntent.Add)
                assertEquals(utc(2026, 10, 14, hour = 0), expectMostRecentItem().pending?.dateUtcMillis)
                vm.onEvent(PhotosIntent.SavePending)
                assertEquals(NOW, expectMostRecentItem().photos.single().takenAt)
            }
        }
    }

    @Test
    fun `the date of a new photo can be moved to an earlier day, never a later one`() = runTest(dispatcher) {
        val repo = FakeProgressPhotoRepository()
        val vm = PhotosViewModel(repo, FakePhotoPicker(PickedPhoto(image, thumb, null)), testClock())
        vm.uiState.test {
            awaitItem()
            vm.onEvent(PhotosIntent.Add)
            vm.onEvent(PhotosIntent.PendingDateChanged(utc(2026, 11, 1, hour = 0)))
            assertEquals(utc(2026, 10, 14, hour = 0), expectMostRecentItem().pending?.dateUtcMillis)
            vm.onEvent(PhotosIntent.PendingDateChanged(utc(2026, 9, 20, hour = 0)))
            vm.onEvent(PhotosIntent.SavePending)
            assertEquals(utc(2026, 9, 20, hour = 12), expectMostRecentItem().photos.single().takenAt)
        }
    }

    @Test
    fun `cancelling the picker or discarding the photo stores nothing`() = runTest(dispatcher) {
        val repo = FakeProgressPhotoRepository()
        val picker = FakePhotoPicker(next = null)
        val vm = PhotosViewModel(repo, picker, testClock())
        vm.uiState.test {
            awaitItem()
            vm.onEvent(PhotosIntent.Add)
            val state = expectMostRecentItem()
            assertNull(state.pending)
            assertFalse(state.busy)
            picker.next = PickedPhoto(image, thumb, null)
            vm.onEvent(PhotosIntent.Add)
            vm.onEvent(PhotosIntent.DiscardPending)
            assertNull(expectMostRecentItem().pending)
            assertTrue(repo.photos.value.isEmpty())
        }
    }

    @Test
    fun `an unreadable photo and a failed write are reported, not crashed on`() = runTest(dispatcher) {
        val repo = FakeProgressPhotoRepository()
        val picker = FakePhotoPicker(failure = IOException("not an image"))
        val vm = PhotosViewModel(repo, picker, testClock())
        vm.uiState.test {
            awaitItem()
            vm.onEvent(PhotosIntent.Add)
            assertEquals(PhotoError.READ, expectMostRecentItem().error)
            vm.onEvent(PhotosIntent.DismissError)
            assertNull(expectMostRecentItem().error)

            picker.failure = null
            picker.next = PickedPhoto(image, thumb, null)
            repo.failWrites = true
            vm.onEvent(PhotosIntent.Add)
            vm.onEvent(PhotosIntent.SavePending)
            val failed = expectMostRecentItem()
            assertEquals(PhotoError.SAVE, failed.error)
            assertNotNull(failed.pending) // kept, so the user can try again
            assertFalse(failed.busy)
            repo.failWrites = false
            vm.onEvent(PhotosIntent.SavePending)
            assertEquals(1, expectMostRecentItem().photos.size)
        }
    }

    @Test
    fun `opening a photo edits its date and note, and the same day keeps its time`() = runTest(dispatcher) {
        val repo = FakeProgressPhotoRepository(three)
        val vm = PhotosViewModel(repo, FakePhotoPicker(), testClock())
        vm.uiState.test {
            assertEquals(listOf("new", "mid", "old"), awaitItem().photos.map { it.id })
            vm.onEvent(PhotosIntent.Open("mid"))
            assertEquals(utc(2026, 9, 1, hour = 0), expectMostRecentItem().detail?.dateUtcMillis)
            vm.onEvent(PhotosIntent.DetailNotesChanged("side"))
            vm.onEvent(PhotosIntent.SaveDetail)
            val saved = expectMostRecentItem()
            assertNull(saved.detail)
            assertEquals(
                three[1].takenAt to "side",
                repo.photos.value.single { it.id == "mid" }.let { it.takenAt to it.notes }
            )

            vm.onEvent(PhotosIntent.Open("mid"))
            vm.onEvent(PhotosIntent.DetailDateChanged(utc(2026, 12, 1, hour = 0))) // future: clamped
            assertEquals(utc(2026, 10, 14, hour = 0), expectMostRecentItem().detail?.dateUtcMillis)
            vm.onEvent(PhotosIntent.DetailDateChanged(utc(2026, 7, 15, hour = 0)))
            vm.onEvent(PhotosIntent.SaveDetail)
            expectMostRecentItem()
            assertEquals(utc(2026, 7, 15, hour = 12), repo.photos.value.single { it.id == "mid" }.takenAt)
            vm.onEvent(PhotosIntent.Open("unknown"))
            assertNull(vm.uiState.value.detail)
        }
    }

    @Test
    fun `deleting asks first and removes the photo`() = runTest(dispatcher) {
        val repo = FakeProgressPhotoRepository(three)
        repo.images["new"] = image
        val vm = PhotosViewModel(repo, FakePhotoPicker(), testClock())
        vm.uiState.test {
            awaitItem()
            vm.onEvent(PhotosIntent.Open("new"))
            vm.onEvent(PhotosIntent.AskDelete)
            assertTrue(expectMostRecentItem().detail?.confirmDelete == true)
            vm.onEvent(PhotosIntent.CancelDelete)
            assertFalse(expectMostRecentItem().detail?.confirmDelete == true)
            assertEquals(3, repo.photos.value.size)
            vm.onEvent(PhotosIntent.AskDelete)
            vm.onEvent(PhotosIntent.ConfirmDelete)
            val state = expectMostRecentItem()
            assertNull(state.detail)
            assertEquals(listOf("mid", "old"), state.photos.map { it.id })
            assertNull(repo.images["new"])
        }
    }

    @Test
    fun `compare puts the first photo against the latest in one tap and each side can change`() = runTest(dispatcher) {
        val repo = FakeProgressPhotoRepository(three)
        val vm = PhotosViewModel(repo, FakePhotoPicker(), testClock())
        vm.uiState.test {
            assertTrue(awaitItem().canCompare)
            vm.onEvent(PhotosIntent.Compare)
            val comparison = assertNotNull(expectMostRecentItem().comparison)
            assertEquals("old" to "new", comparison.leftId to comparison.rightId)
            vm.onEvent(PhotosIntent.ChooseSide(CompareSide.LEFT))
            assertEquals(CompareSide.LEFT, expectMostRecentItem().comparison?.choosing)
            vm.onEvent(PhotosIntent.PickForSide("mid"))
            val left = assertNotNull(expectMostRecentItem().comparison)
            assertEquals(Triple("mid", "new", null), Triple(left.leftId, left.rightId, left.choosing))
            vm.onEvent(PhotosIntent.ChooseSide(CompareSide.RIGHT))
            vm.onEvent(PhotosIntent.PickForSide("old"))
            val right = assertNotNull(expectMostRecentItem().comparison)
            assertEquals("mid" to "old", right.leftId to right.rightId)
            // Picking without a side chosen changes nothing.
            vm.onEvent(PhotosIntent.PickForSide("new"))
            assertEquals("mid" to "old", vm.uiState.value.comparison?.let { it.leftId to it.rightId })
            vm.onEvent(PhotosIntent.CloseComparison)
            assertNull(expectMostRecentItem().comparison)
        }
    }

    @Test
    fun `compare needs two photos and closes when one of its photos is deleted`() = runTest(dispatcher) {
        val repo = FakeProgressPhotoRepository(three.take(1))
        val vm = PhotosViewModel(repo, FakePhotoPicker(), testClock())
        vm.uiState.test {
            assertFalse(awaitItem().canCompare)
            vm.onEvent(PhotosIntent.Compare)
            assertNull(vm.uiState.value.comparison)
            repo.photos.value = three
            vm.onEvent(PhotosIntent.Compare)
            assertNotNull(expectMostRecentItem().comparison)
            repo.delete("new")
            assertNull(expectMostRecentItem().comparison)
        }
    }

    @Test
    fun `images are read through the repository`() = runTest(dispatcher) {
        val repo = FakeProgressPhotoRepository(three)
        repo.images["old"] = image
        val vm = PhotosViewModel(repo, FakePhotoPicker(), testClock())
        assertContentEquals(image, vm.image("old"))
        assertContentEquals(image, vm.thumbnail("old"))
        assertNull(vm.image("missing"))
    }
}
