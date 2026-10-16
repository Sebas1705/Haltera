package es.sebas1705.haltera.export

import co.touchlab.kermit.Logger
import es.sebas1705.haltera.core.common.mvi.MVIBaseIntent
import es.sebas1705.haltera.core.common.mvi.MVIBaseState
import es.sebas1705.haltera.core.common.mvi.MVIBaseViewModel
import es.sebas1705.haltera.data.repository.di.AppVersionName
import es.sebas1705.haltera.data.repository.export.DataExporter
import es.sebas1705.haltera.data.repository.export.ExportCounts
import es.sebas1705.haltera.data.repository.export.ExportFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** What the last export did, shown under the button until dismissed. */
sealed interface ExportOutcome {
    data class Done(val counts: ExportCounts) : ExportOutcome
    data object Failed : ExportOutcome
}

data class DataExportState(val busy: Boolean = false, val outcome: ExportOutcome? = null) : MVIBaseState

sealed interface DataExportIntent : MVIBaseIntent {
    data object Export : DataExportIntent
    data object DismissOutcome : DataExportIntent
}

/** Settings → Data → Export my data (docs/EXPORT-FORMAT.md). */
@OptIn(ExperimentalTime::class)
class DataExportViewModel(
    private val exporter: DataExporter,
    private val files: ExportFileBridge,
    private val appVersion: AppVersionName,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : MVIBaseViewModel<DataExportState, DataExportIntent>(DataExportState()) {

    override fun intentHandler(intent: DataExportIntent): Job = when (intent) {
        DataExportIntent.Export -> execute { export() }
        DataExportIntent.DismissOutcome -> execute { updateState { it.copy(outcome = null) } }
    }

    private suspend fun export() {
        if (currentState.busy) return
        updateState { it.copy(busy = true, outcome = null) }
        val outcome = try {
            val at = now()
            val zone = TimeZone.currentSystemDefault()
            files.save(ExportFormat.fileName(at, zone), MIME_ZIP) { sink ->
                exporter.export(sink, appVersion.value, at, zone)
            }?.let { ExportOutcome.Done(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Logger.w(e) { "Data export failed" }
            ExportOutcome.Failed
        }
        updateState { it.copy(busy = false, outcome = outcome) }
    }

    companion object {
        const val MIME_ZIP = "application/zip"
    }
}
