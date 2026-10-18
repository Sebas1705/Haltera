package es.sebas1705.haltera.feature.progress.ui.photos

import es.sebas1705.haltera.core.common.mvi.MVIBaseIntent
import es.sebas1705.haltera.core.common.mvi.MVIBaseState
import es.sebas1705.haltera.core.common.mvi.MVIBaseViewModel
import es.sebas1705.haltera.data.models.ProgressPhoto
import es.sebas1705.haltera.data.repository.ProgressPhotoRepository
import es.sebas1705.haltera.feature.progress.model.PastSessionTime
import es.sebas1705.haltera.feature.progress.model.ProgressClock
import es.sebas1705.haltera.feature.progress.photos.PickedPhoto
import es.sebas1705.haltera.feature.progress.photos.ProgressPhotoPicker
import kotlinx.coroutines.Job
import kotlin.coroutines.cancellation.CancellationException

/** What went wrong, shown once. */
internal enum class PhotoError { READ, SAVE }

/** A side of the comparison. */
internal enum class CompareSide { LEFT, RIGHT }

/** A picked photo waiting for its date and note before it is stored. */
internal class PendingPhoto(
    val picked: PickedPhoto,
    /** The date as UTC midnight (what the date picker uses). */
    val dateUtcMillis: Long,
    val notes: String = "",
) {
    fun copy(dateUtcMillis: Long = this.dateUtcMillis, notes: String = this.notes) =
        PendingPhoto(picked, dateUtcMillis, notes)
}

/** The photo open in the viewer, with its editable date and note. */
internal data class PhotoDetail(
    val photo: ProgressPhoto,
    val dateUtcMillis: Long,
    val notes: String,
    val confirmDelete: Boolean = false,
)

/** Two photos side by side; [choosing] is the side whose photo is being picked from the list. */
internal data class PhotoComparison(val leftId: String, val rightId: String, val choosing: CompareSide? = null)

internal data class PhotosState(
    val loading: Boolean = true,
    /** Latest first. */
    val photos: List<ProgressPhoto> = emptyList(),
    val pickerAvailable: Boolean = false,
    val busy: Boolean = false,
    val error: PhotoError? = null,
    val pending: PendingPhoto? = null,
    val detail: PhotoDetail? = null,
    val comparison: PhotoComparison? = null,
    /** Today as UTC midnight: the latest date a photo can have. */
    val maxDateUtcMillis: Long = 0,
) : MVIBaseState {
    val canCompare: Boolean get() = photos.size >= 2
}

internal sealed interface PhotosIntent : MVIBaseIntent {
    data object Add : PhotosIntent

    data class PendingDateChanged(val dateUtcMillis: Long) : PhotosIntent

    data class PendingNotesChanged(val text: String) : PhotosIntent

    data object SavePending : PhotosIntent

    data object DiscardPending : PhotosIntent

    data class Open(val id: String) : PhotosIntent

    data class DetailDateChanged(val dateUtcMillis: Long) : PhotosIntent

    data class DetailNotesChanged(val text: String) : PhotosIntent

    data object SaveDetail : PhotosIntent

    data object CloseDetail : PhotosIntent

    data object AskDelete : PhotosIntent

    data object CancelDelete : PhotosIntent

    data object ConfirmDelete : PhotosIntent

    /** One tap: the first photo against the latest. */
    data object Compare : PhotosIntent

    data class ChooseSide(val side: CompareSide?) : PhotosIntent

    data class PickForSide(val id: String) : PhotosIntent

    data object CloseComparison : PhotosIntent

    data object DismissError : PhotosIntent
}

/**
 * Progress photos (roadmap P6): kept on this device only. A picked photo is dated by when it was taken (or today) and
 * the date and note can be changed before and after saving; deleting removes the file. Compare puts two dates side by
 * side, the first and the latest photo by default.
 */
@Suppress("TooManyFunctions") // One small handler per intent.
internal class PhotosViewModel(
    private val photos: ProgressPhotoRepository,
    private val picker: ProgressPhotoPicker,
    private val clock: ProgressClock = ProgressClock(),
) : MVIBaseViewModel<PhotosState, PhotosIntent>(PhotosState(pickerAvailable = picker.isAvailable)) {

    init {
        execute {
            photos.observeAll().collect { list ->
                updateState { state ->
                    val ids = list.mapTo(HashSet()) { it.id }
                    state.copy(
                        loading = false,
                        photos = list,
                        detail = state.detail?.takeIf { it.photo.id in ids },
                        comparison = state.comparison?.takeIf { it.leftId in ids && it.rightId in ids && list.size >= 2 },
                    )
                }
            }
        }
    }

    /** The image files for the screen, read off the main thread by the repository. */
    suspend fun thumbnail(id: String): ByteArray? = photos.thumbnail(id)

    suspend fun image(id: String): ByteArray? = photos.image(id)

    override fun intentHandler(intent: PhotosIntent): Job = execute {
        when (intent) {
            PhotosIntent.Add -> add()
            is PhotosIntent.PendingDateChanged -> updateState { s ->
                s.copy(pending = s.pending?.copy(dateUtcMillis = intent.dateUtcMillis.coerceAtMost(today())))
            }
            is PhotosIntent.PendingNotesChanged -> updateState { s ->
                s.copy(pending = s.pending?.copy(notes = intent.text.take(MAX_NOTES)))
            }
            PhotosIntent.SavePending -> savePending()
            PhotosIntent.DiscardPending -> updateState { it.copy(pending = null) }
            is PhotosIntent.Open -> open(intent.id)
            is PhotosIntent.DetailDateChanged -> updateState { s ->
                s.copy(detail = s.detail?.copy(dateUtcMillis = intent.dateUtcMillis.coerceAtMost(today())))
            }
            is PhotosIntent.DetailNotesChanged -> updateState { s ->
                s.copy(detail = s.detail?.copy(notes = intent.text.take(MAX_NOTES)))
            }
            PhotosIntent.SaveDetail -> saveDetail()
            PhotosIntent.CloseDetail -> updateState { it.copy(detail = null) }
            PhotosIntent.AskDelete -> updateState { s -> s.copy(detail = s.detail?.copy(confirmDelete = true)) }
            PhotosIntent.CancelDelete -> updateState { s -> s.copy(detail = s.detail?.copy(confirmDelete = false)) }
            PhotosIntent.ConfirmDelete -> delete()
            PhotosIntent.Compare -> compare()
            is PhotosIntent.ChooseSide -> updateState { s ->
                s.copy(comparison = s.comparison?.copy(choosing = intent.side))
            }
            is PhotosIntent.PickForSide -> updateState { s -> s.copy(comparison = s.comparison?.pick(intent.id)) }
            PhotosIntent.CloseComparison -> updateState { it.copy(comparison = null) }
            PhotosIntent.DismissError -> updateState { it.copy(error = null) }
        }
    }

    private suspend fun add() {
        if (!picker.isAvailable || currentState.busy) return
        updateState { it.copy(busy = true, error = null) }
        val picked = try {
            picker.pick()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
            updateState { it.copy(busy = false, error = PhotoError.READ) }
            return
        }
        val today = today()
        val date = picked?.takenAt
            ?.let { PastSessionTime.utcMillis(PastSessionTime.localDate(it, clock.timeZone)) }
            ?.coerceAtMost(today)
            ?: today
        updateState {
            it.copy(
                busy = false,
                pending = picked?.let { photo -> PendingPhoto(photo, date) },
                maxDateUtcMillis = today,
            )
        }
    }

    private suspend fun savePending() {
        val pending = currentState.pending ?: return
        if (currentState.busy) return
        updateState { it.copy(busy = true) }
        val saved = try {
            photos.add(pending.picked.image, pending.picked.thumbnail, takenAt(pending), pending.notes)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
            false
        }
        updateState {
            if (saved) it.copy(busy = false, pending = null) else it.copy(busy = false, error = PhotoError.SAVE)
        }
    }

    /** The moment the photo was taken when its date was kept, else now (today) or noon of the chosen day. */
    private fun takenAt(pending: PendingPhoto): Long {
        val original = pending.picked.takenAt
        if (original != null && original <= clock.now() &&
            PastSessionTime.localDate(original, clock.timeZone) == PastSessionTime.dateOf(pending.dateUtcMillis)
        ) {
            return original
        }
        return PastSessionTime.measuredAt(pending.dateUtcMillis, clock.now(), clock.timeZone)
    }

    private fun open(id: String) {
        val photo = currentState.photos.firstOrNull { it.id == id } ?: return
        val date = PastSessionTime.utcMillis(PastSessionTime.localDate(photo.takenAt, clock.timeZone))
        updateState {
            it.copy(
                detail = PhotoDetail(photo, date.coerceAtMost(today()), photo.notes),
                maxDateUtcMillis = today(),
            )
        }
    }

    private suspend fun saveDetail() {
        val detail = currentState.detail ?: return
        val photo = detail.photo
        val sameDay = PastSessionTime.localDate(photo.takenAt, clock.timeZone) == PastSessionTime.dateOf(detail.dateUtcMillis)
        val takenAt = if (sameDay) {
            photo.takenAt
        } else {
            PastSessionTime.measuredAt(
                detail.dateUtcMillis,
                clock.now(),
                clock.timeZone
            )
        }
        val saved = try {
            photos.update(photo.copy(takenAt = takenAt, notes = detail.notes))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
            false
        }
        updateState { if (saved) it.copy(detail = null) else it.copy(error = PhotoError.SAVE) }
    }

    private suspend fun delete() {
        val id = currentState.detail?.photo?.id ?: return
        try {
            photos.delete(id)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
            updateState { it.copy(error = PhotoError.SAVE) }
            return
        }
        updateState { it.copy(detail = null) }
    }

    private fun compare() {
        val list = currentState.photos
        if (list.size < 2) return
        // Latest first: the oldest photo on the left, the latest on the right.
        updateState { it.copy(comparison = PhotoComparison(leftId = list.last().id, rightId = list.first().id)) }
    }

    private fun PhotoComparison.pick(id: String): PhotoComparison = when (choosing) {
        CompareSide.LEFT -> copy(leftId = id, choosing = null)
        CompareSide.RIGHT -> copy(rightId = id, choosing = null)
        null -> this
    }

    private fun today(): Long = PastSessionTime.todayUtcMillis(clock.now(), clock.timeZone)

    companion object {
        const val MAX_NOTES = 200
    }
}
