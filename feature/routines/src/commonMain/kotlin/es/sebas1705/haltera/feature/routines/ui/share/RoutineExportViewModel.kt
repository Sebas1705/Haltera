package es.sebas1705.haltera.feature.routines.ui.share

import es.sebas1705.haltera.core.common.mvi.MVIBaseIntent
import es.sebas1705.haltera.core.common.mvi.MVIBaseState
import es.sebas1705.haltera.core.common.mvi.MVIBaseViewModel
import es.sebas1705.haltera.data.repository.RoutineRepository
import es.sebas1705.haltera.data.repository.share.RoutineShareCode
import es.sebas1705.haltera.data.repository.share.RoutineSharing
import es.sebas1705.haltera.feature.routines.model.attempt
import es.sebas1705.haltera.feature.routines.share.RoutineShareBridge
import kotlinx.coroutines.Job

/** The QR shown full screen; [code] is null when the routine is too large for a QR code. */
internal data class QrState(val name: String, val code: String?)

internal data class RoutineExportState(
    val qr: QrState? = null,
    val busy: Boolean = false,
    val error: Boolean = false,
) : MVIBaseState

internal sealed interface RoutineExportIntent : MVIBaseIntent {
    data class ShareFile(val routineId: String) : RoutineExportIntent
    data class ShowQr(val routineId: String) : RoutineExportIntent
    data object DismissQr : RoutineExportIntent
    data object ErrorShown : RoutineExportIntent
}

/** Exports a routine as a `.haltera` file through the share sheet, or as a QR code. */
internal class RoutineExportViewModel(
    private val routines: RoutineRepository,
    private val sharing: RoutineSharing,
    private val bridge: RoutineShareBridge,
) : MVIBaseViewModel<RoutineExportState, RoutineExportIntent>(RoutineExportState()) {

    override fun intentHandler(intent: RoutineExportIntent): Job = execute {
        when (intent) {
            is RoutineExportIntent.ShareFile -> guarded {
                val name = routines.getRoutine(intent.routineId)?.name.orEmpty()
                val json = checkNotNull(sharing.export(intent.routineId)) { "missing routine" }
                bridge.shareFile(fileName(name), json)
            }
            is RoutineExportIntent.ShowQr -> guarded {
                val name = routines.getRoutine(intent.routineId)?.name.orEmpty()
                // A template code for an unmodified catalogue copy, else base45 (QR alphanumeric mode).
                val code = checkNotNull(sharing.shareCode(intent.routineId)) { "missing routine" }
                    .takeIf { it.length <= RoutineShareCode.MAX_QR_LENGTH }
                updateState { it.copy(qr = QrState(name, code)) }
            }
            RoutineExportIntent.DismissQr -> updateState { it.copy(qr = null) }
            RoutineExportIntent.ErrorShown -> updateState { it.copy(error = false) }
        }
    }

    private suspend fun guarded(block: suspend () -> Unit) {
        if (currentState.busy) return
        updateState { it.copy(busy = true) }
        val result = attempt { block() }
        updateState { it.copy(busy = false, error = result.isFailure) }
    }

    companion object {
        const val EXTENSION = ".haltera"
        private const val MAX_NAME = 60

        /** A safe file name for [routineName]: letters, digits, spaces, dashes and underscores. */
        fun fileName(routineName: String): String {
            val base = routineName.filter { it.isLetterOrDigit() || it in " -_" }.trim().take(MAX_NAME)
            return base.ifEmpty { "routine" } + EXTENSION
        }
    }
}
