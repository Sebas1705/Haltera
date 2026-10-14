package es.sebas1705.ironlog.feature.progress.ui

import app.cash.turbine.test
import es.sebas1705.ironlog.core.units.WeightUnit
import es.sebas1705.ironlog.core.units.Weights
import es.sebas1705.ironlog.data.models.BodyWeightEntry
import es.sebas1705.ironlog.feature.progress.MainDispatcherTest
import es.sebas1705.ironlog.feature.progress.NOW
import es.sebas1705.ironlog.feature.progress.fakes.FakeBodyWeightRepository
import es.sebas1705.ironlog.feature.progress.fakes.FakeSettingsRepository
import es.sebas1705.ironlog.feature.progress.ui.bodyweight.BodyWeightIntent
import es.sebas1705.ironlog.feature.progress.ui.bodyweight.BodyWeightViewModel
import es.sebas1705.ironlog.feature.progress.ui.bodyweight.WeightDialog
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BodyWeightViewModelTest : MainDispatcherTest() {
    private val day = 86_400_000L

    /** Eight daily entries: 80, 81, … 87 kg, the last one today. */
    private val entries = (0 until 8).map { i -> BodyWeightEntry("e$i", NOW - (7 - i) * day, 80_000L + i * 1_000) }

    @Test
    fun `state has entries latest first, a 7-entry moving average and the 30-day change`() = runTest(dispatcher) {
        val vm = BodyWeightViewModel(FakeBodyWeightRepository(entries), FakeSettingsRepository())
        vm.uiState.test {
            val state = awaitItem()
            assertEquals("e7", state.entries.first().id)
            assertEquals(8, state.chart.raw.size)
            assertEquals(entries.map { it.measuredAt }, state.chart.average.map { it.x })
            assertEquals(80_000.0, state.chart.average.first().y)
            // Last value: mean of 81…87 kg.
            assertEquals(84_000.0, state.chart.average.last().y)
            assertEquals(7_000L, state.change30Days)
        }
    }

    @Test
    fun `a weight typed in pounds is stored in grams`() = runTest(dispatcher) {
        val repo = FakeBodyWeightRepository(now = NOW)
        val vm = BodyWeightViewModel(repo, FakeSettingsRepository(WeightUnit.LB))
        vm.uiState.test {
            awaitItem()
            vm.onEvent(BodyWeightIntent.OpenAdd)
            vm.onEvent(BodyWeightIntent.InputChanged("180,5"))
            vm.onEvent(BodyWeightIntent.Save)
            val state = expectMostRecentItem()
            assertNull(state.dialog)
            assertEquals(Weights.lbToGrams(180.5), state.entries.single().weightGrams)
            assertEquals(NOW, repo.entries.value.single().measuredAt)
        }
    }

    @Test
    fun `invalid input keeps the dialog open with an error`() = runTest(dispatcher) {
        val repo = FakeBodyWeightRepository()
        val vm = BodyWeightViewModel(repo, FakeSettingsRepository())
        vm.uiState.test {
            awaitItem()
            vm.onEvent(BodyWeightIntent.OpenAdd)
            vm.onEvent(BodyWeightIntent.InputChanged("abc"))
            vm.onEvent(BodyWeightIntent.Save)
            val state = expectMostRecentItem()
            assertTrue(state.inputError)
            assertEquals(WeightDialog.Add, state.dialog)
            assertTrue(repo.entries.value.isEmpty())
        }
    }

    @Test
    fun `editing prefills the entry in the user's unit and saves the new weight`() = runTest(dispatcher) {
        val repo = FakeBodyWeightRepository(entries)
        val vm = BodyWeightViewModel(repo, FakeSettingsRepository())
        vm.uiState.test {
            awaitItem()
            vm.onEvent(BodyWeightIntent.OpenEdit(entries[7]))
            assertEquals("87", expectMostRecentItem().input)
            vm.onEvent(BodyWeightIntent.InputChanged("86.4"))
            vm.onEvent(BodyWeightIntent.Save)
            val state = expectMostRecentItem()
            assertNull(state.dialog)
            assertEquals(86_400L, state.entries.first().weightGrams)
            assertEquals(entries[7].measuredAt, state.entries.first().measuredAt)
        }
    }

    @Test
    fun `deleting removes the entry`() = runTest(dispatcher) {
        val repo = FakeBodyWeightRepository(entries)
        val vm = BodyWeightViewModel(repo, FakeSettingsRepository())
        vm.uiState.test {
            awaitItem()
            vm.onEvent(BodyWeightIntent.Delete("e7"))
            val state = expectMostRecentItem()
            assertEquals(7, state.entries.size)
            assertEquals("e6", state.entries.first().id)
        }
    }

    @Test
    fun `parse accepts dot or comma and rejects implausible values`() {
        assertEquals(72_500L, BodyWeightViewModel.parseWeight(" 72,5 ", WeightUnit.KG))
        assertEquals(72_500L, BodyWeightViewModel.parseWeight("72.5", WeightUnit.KG))
        assertNull(BodyWeightViewModel.parseWeight("0", WeightUnit.KG))
        assertNull(BodyWeightViewModel.parseWeight("-3", WeightUnit.KG))
        assertNull(BodyWeightViewModel.parseWeight("1000", WeightUnit.KG))
        assertNull(BodyWeightViewModel.parseWeight("", WeightUnit.KG))
    }
}
