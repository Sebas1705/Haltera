package es.sebas1705.haltera.feature.progress.ui

import app.cash.turbine.test
import es.sebas1705.haltera.core.datastore.HealthDataType
import es.sebas1705.haltera.core.datastore.HealthSettings
import es.sebas1705.haltera.data.repository.health.HealthAvailability
import es.sebas1705.haltera.data.repository.health.HealthStatus
import es.sebas1705.haltera.data.models.HealthDay
import es.sebas1705.haltera.domain.usecases.stats.DatedValue
import es.sebas1705.haltera.feature.progress.MainDispatcherTest
import es.sebas1705.haltera.feature.progress.fakes.FakeHealthRepository
import es.sebas1705.haltera.feature.progress.fakes.FakeHealthSettingsStore
import es.sebas1705.haltera.feature.progress.testClock
import es.sebas1705.haltera.feature.progress.ui.activity.ActivityIntent
import es.sebas1705.haltera.feature.progress.ui.activity.ActivityRange
import es.sebas1705.haltera.feature.progress.ui.activity.ActivityViewModel
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivityViewModelTest : MainDispatcherTest() {
    // testClock(): Wednesday 14 October 2026.
    private val today = LocalDate(2026, 10, 14)

    private fun ago(n: Int) = today.minus(DatePeriod(days = n))

    private val days = listOf(
        HealthDay(
            today,
            steps = 4_000,
            activeKcal = 300.0,
            totalKcal = 2_300.0,
            restingHeartRate = 55,
            sleepMinutes = 420
        ),
        HealthDay(ago(1), steps = 9_000, totalKcal = 2_500.0, restingHeartRate = 57, sleepMinutes = 480),
        HealthDay(ago(2), steps = 8_000, restingHeartRate = 59),
        HealthDay(ago(3), steps = null),
        HealthDay(ago(4), steps = 12_000),
        HealthDay(ago(40), steps = 30_000),
    )

    private fun vm(
        health: FakeHealthRepository = FakeHealthRepository(days),
        store: FakeHealthSettingsStore = FakeHealthSettingsStore(),
    ) = ActivityViewModel(health, store, testClock())

    @Test
    fun `the summary has today, the average, the streak, the best day and the goal days`() = runTest(dispatcher) {
        vm().uiState.test {
            val state = awaitItem()
            assertFalse(state.loading)
            assertTrue(state.connected)
            val s = state.summary!!
            assertEquals(4_000L, s.todaySteps)
            assertEquals((9_000 + 8_000 + 12_000) / 3.0, s.sevenDayAverage)
            assertEquals(2, s.currentStreak) // Yesterday and the day before; today is below the goal.
            assertEquals(2, s.longestStreak)
            assertEquals(DatedValue(ago(4), 12_000), s.best)
            assertEquals(3, s.daysAtGoal)
            assertEquals(30, s.daily.size)
            assertEquals(ago(29), s.daily.first().first)
            assertNull(s.daily.single { it.first == ago(3) }.second)
            // 8 weeks shown, minus the empty one before the first day read (Aug 24).
            assertEquals(7, s.weekly.size)
        }
    }

    @Test
    fun `the trends are the days with data, oldest first, with their 7-day averages`() = runTest(dispatcher) {
        vm().uiState.test {
            val s = awaitItem().summary!!
            assertEquals(listOf(ago(2), ago(1), today), s.restingHeartRate.map { it.first })
            assertEquals(58.0, s.restingHeartRateAverage) // 57 and 59, today excluded.
            assertEquals(listOf(8.0, 7.0), s.sleepHours.map { it.second })
            assertEquals(8.0, s.sleepHoursAverage)
            assertEquals(listOf(today), s.activeKcal.map { it.first })
            assertEquals(2, s.totalKcal.size)
        }
    }

    @Test
    fun `the range changes the period, a 90-day range reaches older days`() = runTest(dispatcher) {
        val vm = vm()
        vm.onEvent(ActivityIntent.SetRange(ActivityRange.QUARTER))
        val s = vm.uiState.value.summary!!
        assertEquals(ActivityRange.QUARTER, vm.uiState.value.range)
        assertEquals(90, s.daily.size)
        assertEquals(DatedValue(ago(40), 30_000), s.best)
        vm.onEvent(ActivityIntent.SetRange(ActivityRange.WEEK))
        assertEquals(7, vm.uiState.value.summary!!.daily.size)
    }

    @Test
    fun `opening the tab asks for a throttled read, the button for a forced one`() = runTest(dispatcher) {
        val health = FakeHealthRepository(days)
        val vm = vm(health)
        assertEquals(listOf(false), health.refreshes)
        vm.onEvent(ActivityIntent.Refresh)
        assertEquals(listOf(false, true), health.refreshes)
    }

    @Test
    fun `not connected says so`() = runTest(dispatcher) {
        val vm = vm(FakeHealthRepository(emptyList()), FakeHealthSettingsStore(HealthSettings(connected = false)))
        assertFalse(vm.uiState.value.connected)
        assertFalse(vm.uiState.value.summary!!.hasSteps)
    }

    @Test
    fun `empty Health Connect shows no steps and no streak`() = runTest(dispatcher) {
        val s = vm(FakeHealthRepository(emptyList())).uiState.value.summary!!
        assertFalse(s.hasSteps)
        assertNull(s.todaySteps)
        assertNull(s.sevenDayAverage)
        assertNull(s.best)
        assertEquals(0, s.currentStreak)
        assertTrue(s.restingHeartRate.isEmpty())
        assertTrue(s.weekly.isEmpty())
    }

    @Test
    fun `the steps goal can be changed and drives the streak`() = runTest(dispatcher) {
        val store = FakeHealthSettingsStore()
        val vm = vm(store = store)
        vm.onEvent(ActivityIntent.OpenGoal)
        assertEquals("8000", vm.uiState.value.goalInput)
        vm.onEvent(ActivityIntent.GoalChanged("3 5o00"))
        assertEquals("3500", vm.uiState.value.goalInput)
        vm.onEvent(ActivityIntent.SaveGoal)
        assertNull(vm.uiState.value.goalInput)
        assertEquals(3_500, store.settings.value.stepGoal)
        assertEquals(3_500, vm.uiState.value.stepGoal)
        // Today (4 000) now reaches the goal too: 3 days in a row up to today.
        assertEquals(3, vm.uiState.value.summary!!.currentStreak)
    }

    @Test
    fun `an implausible goal is refused and the dialog stays open`() = runTest(dispatcher) {
        val store = FakeHealthSettingsStore()
        val vm = vm(store = store)
        vm.onEvent(ActivityIntent.OpenGoal)
        vm.onEvent(ActivityIntent.GoalChanged("10"))
        vm.onEvent(ActivityIntent.SaveGoal)
        assertTrue(vm.uiState.value.goalInvalid)
        assertEquals("10", vm.uiState.value.goalInput)
        assertEquals(8_000, store.settings.value.stepGoal)
        vm.onEvent(ActivityIntent.GoalChanged(""))
        vm.onEvent(ActivityIntent.SaveGoal)
        assertTrue(vm.uiState.value.goalInvalid)
        vm.onEvent(ActivityIntent.DismissGoal)
        assertNull(vm.uiState.value.goalInput)
    }

    @Test
    fun `steps not allowed or switched off are told apart from no data`() = runTest(dispatcher) {
        val health = FakeHealthRepository(emptyList())
        val store = FakeHealthSettingsStore()
        val vm = vm(health, store)
        assertTrue(vm.uiState.value.stepsAllowed) // Status not checked yet.
        health.status.value = HealthStatus(HealthAvailability.AVAILABLE, granted = setOf(HealthDataType.CALORIES))
        assertFalse(vm.uiState.value.stepsAllowed)
        health.status.value = HealthStatus(HealthAvailability.AVAILABLE, granted = setOf(HealthDataType.STEPS))
        assertTrue(vm.uiState.value.stepsAllowed)
        store.update { it.copy(enabled = it.enabled - HealthDataType.STEPS) }
        assertFalse(vm.uiState.value.stepsAllowed)
    }

    @Test
    fun `new days from a read update the tab`() = runTest(dispatcher) {
        val health = FakeHealthRepository(emptyList())
        val vm = vm(health)
        assertNull(vm.uiState.value.summary!!.todaySteps)
        health.days.value = listOf(HealthDay(today, steps = 1_000))
        assertEquals(1_000L, vm.uiState.value.summary!!.todaySteps)
    }
}
