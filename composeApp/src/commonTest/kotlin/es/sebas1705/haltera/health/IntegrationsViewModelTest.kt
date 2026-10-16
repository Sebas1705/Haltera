package es.sebas1705.haltera.health

import com.russhwolf.settings.MapSettings
import es.sebas1705.haltera.core.datastore.HealthDataType
import es.sebas1705.haltera.core.datastore.HealthSettingsStore
import es.sebas1705.haltera.core.datastore.SettingsHealthSettingsStore
import es.sebas1705.haltera.data.models.HealthDay
import es.sebas1705.haltera.data.repository.health.HealthAvailability
import es.sebas1705.haltera.data.repository.health.HealthRepository
import es.sebas1705.haltera.data.repository.health.HealthStatus
import es.sebas1705.haltera.data.repository.health.HealthSyncError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A platform stand-in: what the user grants on the permission screen and how the repository reacts. */
private class FakePlatform(val store: HealthSettingsStore) : HealthRepository, HealthConnectBridge {
    var availability = HealthAvailability.AVAILABLE
    var granted = emptySet<HealthDataType>()

    /** What the user taps "Allow" on in the next permission screen. */
    var userAllows: Set<HealthDataType> = HealthDataType.entries.toSet()
    val requested = mutableListOf<Set<HealthDataType>>()
    val refreshes = mutableListOf<Boolean>()
    var installOpened = 0
    var manageOpened = 0
    var disconnected = 0
    override val status = MutableStateFlow(HealthStatus())

    override suspend fun requestPermissions(types: Set<HealthDataType>) {
        requested += types
        granted = granted + (types intersect userAllows)
    }

    override fun openInstallPage() {
        installOpened++
    }

    override fun openManagePermissions() {
        manageOpened++
    }

    override suspend fun checkStatus(): HealthStatus {
        status.update { it.copy(availability = availability, granted = granted) }
        return status.value
    }

    override suspend fun onPermissionsResult() {
        checkStatus()
        if (granted.isNotEmpty()) {
            store.update { it.copy(connected = true) }
            refresh(force = true)
        }
    }

    override suspend fun setEnabled(type: HealthDataType, enabled: Boolean) {
        store.update { if (enabled) it.copy(enabled = it.enabled + type) else it.copy(enabled = it.enabled - type) }
    }

    override suspend fun refresh(force: Boolean) {
        refreshes += force
    }

    override suspend fun writeChanges() = Unit

    override suspend fun watchLocalChanges() = Unit

    override suspend fun disconnect() {
        disconnected++
        granted = emptySet()
        store.update { it.copy(connected = false) }
        status.update { it.copy(granted = emptySet()) }
    }

    override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<HealthDay>> = flowOf(emptyList())
}

@OptIn(ExperimentalCoroutinesApi::class)
class IntegrationsViewModelTest {
    private val store = SettingsHealthSettingsStore(MapSettings())
    private val platform = FakePlatform(store)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = IntegrationsViewModel(platform, store, platform)

    @Test
    fun `nothing is requested until Connect, then only the enabled types`() = runTest {
        val vm = vm()
        assertEquals(HealthAvailability.AVAILABLE, vm.uiState.value.availability)
        assertTrue(platform.requested.isEmpty())
        assertFalse(vm.uiState.value.connected)

        vm.onEvent(IntegrationsIntent.Toggle(HealthDataType.SLEEP, true))
        vm.onEvent(IntegrationsIntent.Toggle(HealthDataType.WORKOUTS_EXPORT, false))
        assertTrue(platform.requested.isEmpty()) // Switches before connecting ask nothing.

        vm.onEvent(IntegrationsIntent.Connect)
        assertEquals(
            setOf(HealthDataType.STEPS, HealthDataType.CALORIES, HealthDataType.WEIGHT_IMPORT, HealthDataType.SLEEP),
            platform.requested.single(),
        )
        assertTrue(vm.uiState.value.connected)
        assertFalse(vm.uiState.value.deniedAll)
        assertEquals(listOf(true), platform.refreshes)
    }

    @Test
    fun `denying everything stays disconnected and says so`() = runTest {
        platform.userAllows = emptySet()
        val vm = vm()
        vm.onEvent(IntegrationsIntent.Connect)
        assertFalse(vm.uiState.value.connected)
        assertTrue(vm.uiState.value.deniedAll)
        assertFalse(vm.uiState.value.requesting)
        assertTrue(platform.refreshes.isEmpty())
    }

    @Test
    fun `a partly denied connect shows which types are not allowed and can ask again`() = runTest {
        platform.userAllows = setOf(HealthDataType.STEPS)
        val vm = vm()
        vm.onEvent(IntegrationsIntent.Connect)
        assertTrue(vm.uiState.value.connected)
        assertEquals(
            setOf(HealthDataType.CALORIES, HealthDataType.WEIGHT_IMPORT, HealthDataType.WORKOUTS_EXPORT),
            vm.uiState.value.missing,
        )
        platform.userAllows = HealthDataType.entries.toSet()
        vm.onEvent(IntegrationsIntent.Allow(HealthDataType.CALORIES))
        assertEquals(setOf(HealthDataType.CALORIES), platform.requested.last())
        assertFalse(HealthDataType.CALORIES in vm.uiState.value.missing)
    }

    @Test
    fun `once connected, switching a type on asks for its permission at once`() = runTest {
        val vm = vm()
        vm.onEvent(IntegrationsIntent.Connect)
        vm.onEvent(IntegrationsIntent.Toggle(HealthDataType.RESTING_HEART_RATE, true))
        assertEquals(setOf(HealthDataType.RESTING_HEART_RATE), platform.requested.last())
        // Already granted: switching it off and on again asks nothing.
        val requests = platform.requested.size
        vm.onEvent(IntegrationsIntent.Toggle(HealthDataType.RESTING_HEART_RATE, false))
        vm.onEvent(IntegrationsIntent.Toggle(HealthDataType.RESTING_HEART_RATE, true))
        assertEquals(requests, platform.requested.size)
    }

    @Test
    fun `permissions revoked in Health Connect show as no access`() = runTest {
        val vm = vm()
        vm.onEvent(IntegrationsIntent.Connect)
        platform.granted = emptySet()
        vm.onEvent(IntegrationsIntent.Refresh)
        assertTrue(vm.uiState.value.noAccess)
        assertEquals(store.settings.value.enabled, vm.uiState.value.missing)
    }

    @Test
    fun `read errors from the repository reach the screen`() = runTest {
        val vm = vm()
        platform.status.update { it.copy(lastError = HealthSyncError.FAILED, syncing = false) }
        assertEquals(HealthSyncError.FAILED, vm.uiState.value.lastError)
        platform.status.update { it.copy(syncing = true) }
        assertTrue(vm.uiState.value.syncing)
    }

    @Test
    fun `Health Connect missing offers the install page, unsupported offers nothing`() = runTest {
        platform.availability = HealthAvailability.PROVIDER_MISSING
        val vm = vm()
        assertEquals(HealthAvailability.PROVIDER_MISSING, vm.uiState.value.availability)
        vm.onEvent(IntegrationsIntent.Install)
        assertEquals(1, platform.installOpened)
        platform.availability = HealthAvailability.UNSUPPORTED
        vm.onEvent(IntegrationsIntent.Refresh)
        assertEquals(HealthAvailability.UNSUPPORTED, vm.uiState.value.availability)
    }

    @Test
    fun `read now forces a read and manage opens Health Connect`() = runTest {
        val vm = vm()
        vm.onEvent(IntegrationsIntent.Connect)
        vm.onEvent(IntegrationsIntent.ReadNow)
        assertEquals(listOf(true, true), platform.refreshes)
        vm.onEvent(IntegrationsIntent.ManagePermissions)
        assertEquals(1, platform.manageOpened)
    }

    @Test
    fun `disconnect asks first and can be cancelled`() = runTest {
        val vm = vm()
        vm.onEvent(IntegrationsIntent.Connect)
        vm.onEvent(IntegrationsIntent.AskDisconnect)
        assertTrue(vm.uiState.value.confirmDisconnect)
        vm.onEvent(IntegrationsIntent.DismissDisconnect)
        assertFalse(vm.uiState.value.confirmDisconnect)
        assertEquals(0, platform.disconnected)
        vm.onEvent(IntegrationsIntent.AskDisconnect)
        vm.onEvent(IntegrationsIntent.ConfirmDisconnect)
        assertEquals(1, platform.disconnected)
        assertFalse(vm.uiState.value.connected)
        assertFalse(vm.uiState.value.confirmDisconnect)
    }

    @Test
    fun `the steps goal is edited with validation`() = runTest {
        val vm = vm()
        vm.onEvent(IntegrationsIntent.OpenGoal)
        assertEquals("8000", vm.uiState.value.goalInput)
        vm.onEvent(IntegrationsIntent.GoalChanged("100"))
        vm.onEvent(IntegrationsIntent.SaveGoal)
        assertTrue(vm.uiState.value.goalInvalid)
        vm.onEvent(IntegrationsIntent.GoalChanged("10.000"))
        assertEquals("10000", vm.uiState.value.goalInput)
        vm.onEvent(IntegrationsIntent.SaveGoal)
        assertNull(vm.uiState.value.goalInput)
        assertEquals(10_000, store.settings.value.stepGoal)
        assertEquals(10_000, vm.uiState.value.stepGoal)
        vm.onEvent(IntegrationsIntent.OpenGoal)
        vm.onEvent(IntegrationsIntent.DismissGoal)
        assertNull(vm.uiState.value.goalInput)
    }

    @Test
    fun `connect with every type switched off asks nothing`() = runTest {
        val vm = vm()
        store.update { it.copy(enabled = emptySet()) }
        vm.onEvent(IntegrationsIntent.Connect)
        assertTrue(platform.requested.isEmpty())
        assertFalse(vm.uiState.value.connected)
    }
}
