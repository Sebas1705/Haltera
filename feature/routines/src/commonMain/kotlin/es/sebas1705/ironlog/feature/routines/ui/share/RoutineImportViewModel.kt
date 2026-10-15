package es.sebas1705.ironlog.feature.routines.ui.share

import es.sebas1705.ironlog.core.common.mvi.MVIBaseIntent
import es.sebas1705.ironlog.core.common.mvi.MVIBaseState
import es.sebas1705.ironlog.core.common.mvi.MVIBaseViewModel
import es.sebas1705.ironlog.data.repository.RoutineRepository
import es.sebas1705.ironlog.data.repository.share.ImportPreview
import es.sebas1705.ironlog.data.repository.share.ImportPreviewResult
import es.sebas1705.ironlog.data.repository.share.RoutineSharing
import es.sebas1705.ironlog.feature.routines.model.AppLanguage
import es.sebas1705.ironlog.feature.routines.model.attempt
import es.sebas1705.ironlog.feature.routines.share.RoutineShareBridge
import kotlinx.coroutines.Job

/** Where the routine to import comes from. */
internal enum class ImportSource { FILE, SCAN, INCOMING }

internal enum class ImportProblem { INVALID, UNSUPPORTED_VERSION, SCAN_UNSUPPORTED }

internal data class RoutineImportState(
    val language: String = "en",
    val loading: Boolean = true,
    /** The user cancelled the picker or scanner: the screen closes. */
    val cancelled: Boolean = false,
    val preview: ImportPreview? = null,
    val problem: ImportProblem? = null,
    /** Leave out slots aimed at exercises missing on this device instead of adding them as custom exercises. */
    val dropMissing: Boolean = false,
    val setActive: Boolean = false,
    val importing: Boolean = false,
    /** One-shot: the new routine, to open in the editor. */
    val importedId: String? = null,
    val error: Boolean = false,
) : MVIBaseState

internal sealed interface RoutineImportIntent : MVIBaseIntent {
    data class SetDropMissing(val drop: Boolean) : RoutineImportIntent
    data class SetActive(val active: Boolean) : RoutineImportIntent
    data object Confirm : RoutineImportIntent
    data object OpenHandled : RoutineImportIntent
    data object ErrorShown : RoutineImportIntent
}

/** Reads a shared routine (file, QR or incoming), previews it and adds it as a NEW routine. */
internal class RoutineImportViewModel(
    source: ImportSource,
    private val sharing: RoutineSharing,
    private val routines: RoutineRepository,
    private val bridge: RoutineShareBridge,
    language: AppLanguage,
) : MVIBaseViewModel<RoutineImportState, RoutineImportIntent>(RoutineImportState(language = language.code())) {

    init {
        execute { load(source) }
    }

    private suspend fun load(source: ImportSource) {
        if (source == ImportSource.SCAN && !bridge.canScanQr) {
            updateState { it.copy(loading = false, problem = ImportProblem.SCAN_UNSUPPORTED) }
            return
        }
        val text = attempt {
            when (source) {
                ImportSource.FILE -> bridge.pickFile()
                ImportSource.SCAN -> bridge.scanQr()
                ImportSource.INCOMING -> bridge.incoming.value.also { bridge.consumeIncoming() }
            }
        }.getOrNull()
        if (text == null) {
            updateState { it.copy(loading = false, cancelled = true) }
            return
        }
        val result = attempt {
            sharing.preview(text, currentState.language)
        }.getOrElse { ImportPreviewResult.Invalid("read failed") }
        updateState {
            when (result) {
                is ImportPreviewResult.Ready -> it.copy(loading = false, preview = result.preview)
                is ImportPreviewResult.UnsupportedVersion ->
                    it.copy(loading = false, problem = ImportProblem.UNSUPPORTED_VERSION)
                is ImportPreviewResult.Invalid -> it.copy(loading = false, problem = ImportProblem.INVALID)
            }
        }
    }

    override fun intentHandler(intent: RoutineImportIntent): Job = execute {
        when (intent) {
            is RoutineImportIntent.SetDropMissing -> updateState { it.copy(dropMissing = intent.drop) }
            is RoutineImportIntent.SetActive -> updateState { it.copy(setActive = intent.active) }
            RoutineImportIntent.Confirm -> confirm()
            RoutineImportIntent.OpenHandled -> updateState { it.copy(importedId = null) }
            RoutineImportIntent.ErrorShown -> updateState { it.copy(error = false) }
        }
    }

    private suspend fun confirm() {
        val state = currentState
        val preview = state.preview ?: return
        if (state.importing) return
        updateState { it.copy(importing = true) }
        attempt {
            sharing.import(preview, state.dropMissing).also { id -> if (state.setActive) routines.setActive(id) }
        }
            .onSuccess { id -> updateState { it.copy(importing = false, importedId = id) } }
            .onFailure { updateState { it.copy(importing = false, error = true) } }
    }
}
