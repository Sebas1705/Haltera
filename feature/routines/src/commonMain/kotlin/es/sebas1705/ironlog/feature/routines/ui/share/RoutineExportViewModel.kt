package es.sebas1705.ironlog.feature.routines.ui.share

import es.sebas1705.ironlog.core.common.mvi.MVIBaseIntent
import es.sebas1705.ironlog.core.common.mvi.MVIBaseState
import es.sebas1705.ironlog.core.common.mvi.MVIBaseViewModel
import es.sebas1705.ironlog.data.repository.RoutineRepository
import es.sebas1705.ironlog.data.repository.share.RoutineShareCode
import es.sebas1705.ironlog.data.repository.share.RoutineSharing
import es.sebas1705.ironlog.feature.routines.model.attempt
import es.sebas1705.ironlog.feature.routines.share.RoutineShareBridge
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

/** Exports a routine as a `.ironlog` file through the share sheet, or as a QR code. */
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
                val json = checkNotNull(sharing.export(intent.routineId)) { "missing routine" }
                val code = RoutineShareCode.encode(json).takeIf { it.length <= RoutineShareCode.MAX_QR_LENGTH }
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
        const val EXTENSION = ".ironlog"
        private const val MAX_NAME = 60

        /** A safe file name for [routineName]: letters, digits, spaces, dashes and underscores. */
        fun fileName(routineName: String): String {
            val base = routineName.filter { it.isLetterOrDigit() || it in " -_" }.trim().take(MAX_NAME)
            return base.ifEmpty { "routine" } + EXTENSION
        }
    }
}
