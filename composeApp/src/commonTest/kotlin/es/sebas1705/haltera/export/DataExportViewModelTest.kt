package es.sebas1705.haltera.export

import es.sebas1705.haltera.data.repository.di.AppVersionName
import es.sebas1705.haltera.data.repository.export.ByteSink
import es.sebas1705.haltera.data.repository.export.DataExporter
import es.sebas1705.haltera.data.repository.export.ExportCounts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeExporter(var fail: Boolean = false) : DataExporter {
    var calls = 0
    var version: String? = null

    override suspend fun export(sink: ByteSink, appVersion: String, exportedAt: Long, timeZone: TimeZone): ExportCounts {
        calls++
        version = appVersion
        check(!fail) { "disk full" }
        return ExportCounts(sessions = 2, sets = 7, bodyWeightEntries = 3, mealEntries = 4)
    }
}

private class FakeFiles(var cancel: Boolean = false) : ExportFileBridge {
    var fileName: String? = null

    override suspend fun <T : Any> save(fileName: String, mimeType: String, write: suspend (ByteSink) -> T): T? {
        this.fileName = fileName
        if (cancel) return null
        return write(
            object : ByteSink {
                override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
            },
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DataExportViewModelTest {
    private val exporter = FakeExporter()
    private val files = FakeFiles()

    // 2026-10-16T09:15:30Z; the zone is the device's, so only the shape of the name is checked.
    private fun viewModel() = DataExportViewModel(
        exporter,
        files,
        AppVersionName("1.0.0"),
        now = { 1_792_142_130_000L }
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `exporting names the file by date, passes the app version and reports the counts`() {
        val vm = viewModel()

        vm.onEvent(DataExportIntent.Export)

        val name = files.fileName.orEmpty()
        assertTrue(Regex("haltera-export-\\d{4}-\\d{2}-\\d{2}\\.zip").matches(name), name)
        assertEquals("1.0.0", exporter.version)
        assertFalse(vm.uiState.value.busy)
        assertEquals(ExportOutcome.Done(ExportCounts(2, 7, 3, 4)), vm.uiState.value.outcome)
    }

    @Test
    fun `cancelling the picker exports nothing and shows nothing`() {
        files.cancel = true
        val vm = viewModel()

        vm.onEvent(DataExportIntent.Export)

        assertEquals(0, exporter.calls)
        assertNull(vm.uiState.value.outcome)
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun `a failure is reported and can be dismissed`() {
        exporter.fail = true
        val vm = viewModel()

        vm.onEvent(DataExportIntent.Export)
        assertEquals(ExportOutcome.Failed, vm.uiState.value.outcome)
        assertFalse(vm.uiState.value.busy)

        vm.onEvent(DataExportIntent.DismissOutcome)
        assertNull(vm.uiState.value.outcome)
    }
}
