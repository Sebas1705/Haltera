package es.sebas1705.haltera.settings

import com.russhwolf.settings.MapSettings
import es.sebas1705.haltera.core.datastore.KeyValueSettingsRepository
import es.sebas1705.haltera.core.datastore.SettingsCrashReportsStore
import es.sebas1705.haltera.core.units.WeightUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val settings = MapSettings()
    private val repository = KeyValueSettingsRepository(settings)
    private val crashReports = SettingsCrashReportsStore(settings)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(available: Boolean) =
        SettingsViewModel(repository, crashReports, CrashReportsAvailability(available))

    @Test
    fun `crash reports are on by default and the switch shows only where there is a reporter`() {
        assertTrue(viewModel(available = true).uiState.value.crashReportsAvailable)
        assertTrue(viewModel(available = true).uiState.value.crashReports)
        assertFalse(viewModel(available = false).uiState.value.crashReportsAvailable)
    }

    @Test
    fun `turning crash reports off is stored and shown`() = runTest {
        val vm = viewModel(available = true)
        vm.onEvent(SettingsIntent.SetCrashReports(false))
        assertFalse(vm.uiState.value.crashReports)
        assertFalse(SettingsCrashReportsStore(settings).enabled.value)

        vm.onEvent(SettingsIntent.SetCrashReports(true))
        assertTrue(vm.uiState.value.crashReports)
        assertTrue(SettingsCrashReportsStore(settings).enabled.value)
    }

    @Test
    fun `a choice made before opening Settings is shown`() {
        crashReports.setEnabled(false)
        assertFalse(viewModel(available = true).uiState.value.crashReports)
    }

    @Test
    fun `the crash reports switch does not touch the synced settings`() = runTest {
        val vm = viewModel(available = true)
        val before = repository.settings.value
        vm.onEvent(SettingsIntent.SetCrashReports(false))
        assertEquals(before, repository.settings.value)

        vm.onEvent(SettingsIntent.SetUnit(WeightUnit.LB))
        assertEquals(WeightUnit.LB, vm.uiState.value.settings.unit)
        assertFalse(vm.uiState.value.crashReports)
    }
}
