package es.sebas1705.haltera.feature.today.ui

import es.sebas1705.haltera.core.datastore.HealthDataType
import es.sebas1705.haltera.core.datastore.HealthSettings
import es.sebas1705.haltera.core.datastore.HealthSettingsStore
import es.sebas1705.haltera.data.models.HealthDay
import es.sebas1705.haltera.data.repository.health.HealthRepository
import es.sebas1705.haltera.data.repository.health.HealthStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

private class FakeHealth(days: List<HealthDay>) : HealthRepository {
    val days = MutableStateFlow(days)
    val refreshes = mutableListOf<Boolean>()
    override val status = MutableStateFlow(HealthStatus())

    override suspend fun checkStatus() = status.value

    override suspend fun onPermissionsResult() = Unit

    override suspend fun setEnabled(type: HealthDataType, enabled: Boolean) = Unit

    override suspend fun refresh(force: Boolean) {
        refreshes += force
    }

    override suspend fun writeChanges() = Unit

    override suspend fun watchLocalChanges() = Unit

    override suspend fun disconnect() = Unit

    override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<HealthDay>> =
        days.map { list -> list.filter { it.date in from..to } }
}

private class FakeStore(initial: HealthSettings) : HealthSettingsStore {
    val state = MutableStateFlow(initial)
    override val settings: StateFlow<HealthSettings> = state

    override fun update(change: (HealthSettings) -> HealthSettings) {
        state.value = change(state.value)
    }
}

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class TodayStepsViewModelTest {
    private val madrid = TimeZone.of("Europe/Madrid")

    // 00:30 on 16 October in Madrid is still the 15th in UTC: the card must use the local day.
    private val now = LocalDateTime(2026, 10, 16, 0, 30).toInstant(madrid).toEpochMilliseconds()
    private val clock = TodayClock(now = { now }, timeZone = madrid)
    private val today = LocalDate(2026, 10, 16)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `hidden until Health Connect steps are connected`() = runTest {
        val store = FakeStore(HealthSettings(connected = false))
        val health = FakeHealth(listOf(HealthDay(today, steps = 3_000)))
        val vm = TodayStepsViewModel(health, store, clock)
        assertFalse(vm.uiState.value.visible)

        store.update { it.copy(connected = true, enabled = setOf(HealthDataType.CALORIES)) }
        assertFalse(vm.uiState.value.visible) // Steps switched off.

        store.update { it.copy(enabled = setOf(HealthDataType.STEPS)) }
        assertTrue(vm.uiState.value.visible)
        assertEquals(3_000L, vm.uiState.value.steps)
    }

    @Test
    fun `shows the local day's steps against the goal and follows new reads`() = runTest {
        val health = FakeHealth(listOf(HealthDay(LocalDate(2026, 10, 15), steps = 12_000)))
        val vm = TodayStepsViewModel(health, FakeStore(HealthSettings(connected = true)), clock)
        assertTrue(vm.uiState.value.visible)
        assertNull(vm.uiState.value.steps) // Yesterday's 12 000 are not today's.
        assertFalse(vm.uiState.value.reached)
        health.days.value = health.days.value + HealthDay(today, steps = 8_000)
        assertEquals(8_000L, vm.uiState.value.steps)
        assertTrue(vm.uiState.value.reached)
        assertEquals(listOf(false), health.refreshes)
    }

    @Test
    fun `a new goal applies at once`() = runTest {
        val store = FakeStore(HealthSettings(connected = true))
        val vm = TodayStepsViewModel(FakeHealth(listOf(HealthDay(today, steps = 9_000))), store, clock)
        assertTrue(vm.uiState.value.reached)
        store.setStepGoal(10_000)
        assertEquals(10_000, vm.uiState.value.goal)
        assertFalse(vm.uiState.value.reached)
    }
}
