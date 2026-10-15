package es.sebas1705.ironlog.feature.routines

import es.sebas1705.ironlog.core.testing.CoroutineTestRule
import es.sebas1705.ironlog.data.models.LocalizedText
import es.sebas1705.ironlog.data.repository.share.ImportPreview
import es.sebas1705.ironlog.data.repository.share.ImportPreviewResult
import es.sebas1705.ironlog.data.repository.share.QrCapacity
import es.sebas1705.ironlog.data.repository.share.RoutineFile
import es.sebas1705.ironlog.data.repository.share.RoutineShareCode
import es.sebas1705.ironlog.data.repository.share.RoutineSharing
import es.sebas1705.ironlog.data.repository.share.SharedRoutine
import es.sebas1705.ironlog.feature.routines.share.RoutineShareBridge
import es.sebas1705.ironlog.feature.routines.ui.share.ImportProblem
import es.sebas1705.ironlog.feature.routines.ui.share.ImportSource
import es.sebas1705.ironlog.feature.routines.ui.share.RoutineExportIntent
import es.sebas1705.ironlog.feature.routines.ui.share.RoutineExportViewModel
import es.sebas1705.ironlog.feature.routines.ui.share.RoutineImportIntent
import es.sebas1705.ironlog.feature.routines.ui.share.RoutineImportViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.random.Random

private class FakeBridge(
    var picked: String? = null,
    var scanned: String? = null,
    override val canScanQr: Boolean = true,
) : RoutineShareBridge {
    override val incoming = MutableStateFlow<String?>(null)
    val shared = mutableListOf<Pair<String, String>>()

    override fun consumeIncoming() {
        incoming.value = null
    }

    override suspend fun shareFile(fileName: String, content: String) {
        shared += fileName to content
    }

    override suspend fun pickFile(): String? = picked

    override suspend fun scanQr(): String? = scanned
}

private class FakeSharing(private val routines: FakeRoutineRepository) : RoutineSharing {
    var exportJson: String? = """{"format":"ironlog-routine"}"""
    var result: ImportPreviewResult = ImportPreviewResult.Ready(PREVIEW)
    val previewed = mutableListOf<String>()
    val languages = mutableListOf<String>()
    val imports = mutableListOf<Boolean>()

    override suspend fun export(routineId: String): String? = exportJson

    override suspend fun preview(text: String, language: String): ImportPreviewResult {
        previewed += text
        languages += language
        return result
    }

    override suspend fun import(preview: ImportPreview, dropMissing: Boolean): String {
        imports += dropMissing
        return routines.createRoutine(preview.file.routine.name)
    }

    companion object {
        val PREVIEW = ImportPreview(
            file = RoutineFile(routine = SharedRoutine(name = "Shared")),
            names = mapOf("squat.barbell" to LocalizedText.same("Squat")),
            missingIds = listOf("zercher.barbell"),
        )
    }
}

class RoutineShareViewModelsTest {
    private val rule = CoroutineTestRule()
    private val routines = FakeRoutineRepository()
    private val sharing = FakeSharing(routines)

    @BeforeTest
    fun setUp() = rule.setup()

    @AfterTest
    fun tearDown() = rule.tearDown()

    @Test
    fun `share file sends the export to the share sheet with a safe name`() = runTest {
        val bridge = FakeBridge()
        val id = routines.createRoutine("Push/Pull: A")
        val vm = RoutineExportViewModel(routines, sharing, bridge)
        vm.onEvent(RoutineExportIntent.ShareFile(id))
        assertEquals(listOf("PushPull A.ironlog" to sharing.exportJson!!), bridge.shared)
        assertFalse(vm.uiState.value.error)
    }

    @Test
    fun `show qr encodes the routine or reports it is too large`() = runTest {
        val id = routines.createRoutine("Legs")
        val vm = RoutineExportViewModel(routines, sharing, FakeBridge())
        vm.onEvent(RoutineExportIntent.ShowQr(id))
        val qr = assertNotNull(vm.uiState.value.qr)
        assertEquals("Legs", qr.name)
        assertEquals(sharing.exportJson, RoutineShareCode.decode(qr.code!!))
        // v2 by default: the whole code is in the QR alphanumeric charset.
        assertTrue(qr.code.orEmpty().startsWith(RoutineShareCode.PREFIX))
        assertTrue(QrCapacity.isAlphanumeric(qr.code.orEmpty()))

        vm.onEvent(RoutineExportIntent.DismissQr)
        assertNull(vm.uiState.value.qr)

        // Random text barely compresses: far beyond what a QR holds.
        val random = Random(1)
        sharing.exportJson = (1..20_000).joinToString("") { (random.nextInt(94) + 33).toChar().toString() }
        vm.onEvent(RoutineExportIntent.ShowQr(id))
        assertNull(vm.uiState.value.qr?.code)
    }

    @Test
    fun `a failed export shows an error`() = runTest {
        sharing.exportJson = null
        val vm = RoutineExportViewModel(routines, sharing, FakeBridge())
        vm.onEvent(RoutineExportIntent.ShareFile("missing"))
        assertTrue(vm.uiState.value.error)
        vm.onEvent(RoutineExportIntent.ErrorShown)
        assertFalse(vm.uiState.value.error)
    }

    @Test
    fun `importing a picked file previews it and adds a new routine`() = runTest {
        val bridge = FakeBridge(picked = "file text")
        val vm = RoutineImportViewModel(ImportSource.FILE, sharing, routines, bridge, english)
        assertEquals(listOf("file text"), sharing.previewed)
        assertEquals(listOf("en"), sharing.languages)
        assertEquals(FakeSharing.PREVIEW, vm.uiState.value.preview)

        vm.onEvent(RoutineImportIntent.SetDropMissing(true))
        vm.onEvent(RoutineImportIntent.SetActive(true))
        vm.onEvent(RoutineImportIntent.Confirm)

        val id = assertNotNull(vm.uiState.value.importedId)
        assertEquals(listOf(true), sharing.imports)
        assertTrue(routines.routine(id).isActive)
        vm.onEvent(RoutineImportIntent.OpenHandled)
        assertNull(vm.uiState.value.importedId)
    }

    @Test
    fun `incoming routines are consumed and a cancelled picker closes the screen`() = runTest {
        val bridge = FakeBridge()
        bridge.incoming.value = "ironlog:r1:abc"
        RoutineImportViewModel(ImportSource.INCOMING, sharing, routines, bridge, english)
        assertEquals(listOf("ironlog:r1:abc"), sharing.previewed)
        assertNull(bridge.incoming.value)

        val cancelled = RoutineImportViewModel(ImportSource.FILE, sharing, routines, bridge, english)
        assertTrue(cancelled.uiState.value.cancelled)
    }

    @Test
    fun `problems are shown instead of a preview`() = runTest {
        sharing.result = ImportPreviewResult.UnsupportedVersion(2)
        val newer = RoutineImportViewModel(ImportSource.SCAN, sharing, routines, FakeBridge(scanned = "x"), english)
        assertEquals(ImportProblem.UNSUPPORTED_VERSION, newer.uiState.value.problem)

        sharing.result = ImportPreviewResult.Invalid("bad")
        val invalid = RoutineImportViewModel(ImportSource.SCAN, sharing, routines, FakeBridge(scanned = "x"), english)
        assertEquals(ImportProblem.INVALID, invalid.uiState.value.problem)

        val noScanner = FakeBridge(canScanQr = false)
        val unsupported = RoutineImportViewModel(ImportSource.SCAN, sharing, routines, noScanner, english)
        assertEquals(ImportProblem.SCAN_UNSUPPORTED, unsupported.uiState.value.problem)

        invalid.onEvent(RoutineImportIntent.Confirm)
        assertTrue(routines.routines.isEmpty())
    }
}
