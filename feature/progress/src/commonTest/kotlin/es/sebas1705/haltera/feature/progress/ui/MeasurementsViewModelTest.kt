package es.sebas1705.haltera.feature.progress.ui

import app.cash.turbine.test
import es.sebas1705.haltera.core.units.LengthUnit
import es.sebas1705.haltera.core.units.WeightUnit
import es.sebas1705.haltera.data.models.BodyMeasurement
import es.sebas1705.haltera.data.models.MeasurementType
import es.sebas1705.haltera.feature.progress.MainDispatcherTest
import es.sebas1705.haltera.feature.progress.NOW
import es.sebas1705.haltera.feature.progress.fakes.FakeBodyMeasurementRepository
import es.sebas1705.haltera.feature.progress.fakes.FakeSettingsRepository
import es.sebas1705.haltera.feature.progress.model.ProgressClock
import es.sebas1705.haltera.feature.progress.testClock
import es.sebas1705.haltera.feature.progress.ui.measurements.MeasurementDialog
import es.sebas1705.haltera.feature.progress.ui.measurements.MeasurementsIntent
import es.sebas1705.haltera.feature.progress.ui.measurements.MeasurementsViewModel
import es.sebas1705.haltera.feature.progress.utc
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeasurementsViewModelTest : MainDispatcherTest() {
    private val day = 86_400_000L

    private val waist = listOf(
        BodyMeasurement("w1", NOW - 20 * day, MeasurementType.WAIST, 840),
        BodyMeasurement("w2", NOW - day, MeasurementType.WAIST, 825),
    )
    private val calf = BodyMeasurement("c1", NOW - 2 * day, MeasurementType.CUSTOM, 370, "Calf")

    private fun vm(
        repo: FakeBodyMeasurementRepository = FakeBodyMeasurementRepository(now = NOW),
        unit: WeightUnit = WeightUnit.KG,
        clock: ProgressClock = testClock(),
    ) = MeasurementsViewModel(repo, FakeSettingsRepository(unit), clock)

    @Test
    fun `an empty log has no series and nothing selected`() = runTest(dispatcher) {
        vm().uiState.test {
            val state = awaitItem()
            assertFalse(state.loading)
            assertTrue(state.series.isEmpty())
            assertNull(state.selected)
            assertEquals(LengthUnit.CM, state.unit)
        }
    }

    @Test
    fun `the series of the latest measurement is shown first, and a choice is kept`() = runTest(dispatcher) {
        val repo = FakeBodyMeasurementRepository(waist + calf, now = NOW)
        val vm = vm(repo)
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(listOf("WAIST", "CUSTOM:calf"), state.series.map { it.key })
            assertEquals("WAIST", state.selectedKey)
            assertEquals(-15L, state.selected?.change30Days)
            assertEquals(listOf("Calf"), state.customNames)
            vm.onEvent(MeasurementsIntent.SelectSeries("CUSTOM:calf"))
            assertEquals("CUSTOM:calf", expectMostRecentItem().selectedKey)
            // Another write elsewhere does not move the choice.
            repo.add(MeasurementType.WAIST, 820, NOW)
            assertEquals("CUSTOM:calf", expectMostRecentItem().selectedKey)
            // Deleting the last entry of the chosen series falls back to the latest one.
            vm.onEvent(MeasurementsIntent.Delete("c1"))
            assertEquals("WAIST", expectMostRecentItem().selectedKey)
        }
    }

    @Test
    fun `pounds show lengths in inches and a value typed in inches is stored in millimetres`() = runTest(dispatcher) {
        val repo = FakeBodyMeasurementRepository(now = NOW)
        val vm = vm(repo, WeightUnit.LB)
        vm.uiState.test {
            assertEquals(LengthUnit.IN, awaitItem().unit)
            vm.onEvent(MeasurementsIntent.OpenAdd)
            vm.onEvent(MeasurementsIntent.InputChanged("32,5"))
            vm.onEvent(MeasurementsIntent.Save)
            val state = expectMostRecentItem()
            assertNull(state.dialog)
            val row = repo.rows.value.single()
            assertEquals(MeasurementType.WAIST to 826L, row.type to row.value)
            assertEquals(NOW, row.measuredAt)
            assertEquals("WAIST", state.selectedKey)
        }
    }

    @Test
    fun `invalid values and missing custom names keep the dialog open with errors`() = runTest(dispatcher) {
        val repo = FakeBodyMeasurementRepository(now = NOW)
        val vm = vm(repo)
        vm.uiState.test {
            awaitItem()
            vm.onEvent(MeasurementsIntent.OpenAdd)
            for (bad in listOf("", "abc", "0", "-3", "500.1")) {
                vm.onEvent(MeasurementsIntent.InputChanged(bad))
                vm.onEvent(MeasurementsIntent.Save)
                val state = expectMostRecentItem()
                assertTrue(state.form.valueError, bad)
                assertEquals(MeasurementDialog.Add, state.dialog)
            }
            vm.onEvent(MeasurementsIntent.TypeChanged(MeasurementType.CUSTOM))
            vm.onEvent(MeasurementsIntent.InputChanged("37"))
            vm.onEvent(MeasurementsIntent.CustomNameChanged("   "))
            vm.onEvent(MeasurementsIntent.Save)
            val state = expectMostRecentItem()
            assertTrue(state.form.nameError)
            assertFalse(state.form.valueError)
            assertTrue(repo.rows.value.isEmpty())
            // Typing clears the error.
            vm.onEvent(MeasurementsIntent.CustomNameChanged("Gemelo"))
            assertFalse(expectMostRecentItem().form.nameError)
        }
    }

    @Test
    fun `body fat is typed in percent and a type change between kinds clears the number`() = runTest(dispatcher) {
        val repo = FakeBodyMeasurementRepository(now = NOW)
        val vm = vm(repo, WeightUnit.LB)
        vm.uiState.test {
            awaitItem()
            vm.onEvent(MeasurementsIntent.OpenAdd)
            vm.onEvent(MeasurementsIntent.InputChanged("35"))
            vm.onEvent(MeasurementsIntent.TypeChanged(MeasurementType.ARM))
            assertEquals("35", expectMostRecentItem().form.input) // length to length keeps it
            vm.onEvent(MeasurementsIntent.TypeChanged(MeasurementType.BODY_FAT))
            assertEquals("", expectMostRecentItem().form.input)
            vm.onEvent(MeasurementsIntent.InputChanged("18,5"))
            vm.onEvent(MeasurementsIntent.Save)
            expectMostRecentItem()
            assertEquals(185L, repo.rows.value.single().value)
        }
    }

    @Test
    fun `a custom measurement is saved with its normalised name, and its chart comes on screen`() = runTest(
        dispatcher
    ) {
        val repo = FakeBodyMeasurementRepository(waist, now = NOW)
        val vm = vm(repo)
        vm.uiState.test {
            awaitItem()
            vm.onEvent(MeasurementsIntent.OpenAdd)
            vm.onEvent(MeasurementsIntent.TypeChanged(MeasurementType.CUSTOM))
            vm.onEvent(MeasurementsIntent.CustomNameChanged("  Gemelo   izq. "))
            vm.onEvent(MeasurementsIntent.InputChanged("37,1"))
            vm.onEvent(MeasurementsIntent.NotesChanged("tape at the widest"))
            vm.onEvent(MeasurementsIntent.Save)
            val state = expectMostRecentItem()
            assertEquals("CUSTOM:gemelo izq.", state.selectedKey)
            val row = repo.rows.value.single { it.type == MeasurementType.CUSTOM }
            assertEquals(
                Triple("Gemelo izq.", 371L, "tape at the widest"),
                Triple(row.customName, row.value, row.notes)
            )
        }
    }

    @Test
    fun `a new measurement opens on the series on screen, custom name included`() = runTest(dispatcher) {
        val vm = vm(FakeBodyMeasurementRepository(waist + calf, now = NOW))
        vm.uiState.test {
            awaitItem()
            vm.onEvent(MeasurementsIntent.SelectSeries("CUSTOM:calf"))
            vm.onEvent(MeasurementsIntent.OpenAdd)
            val form = expectMostRecentItem().form
            assertEquals(MeasurementType.CUSTOM to "Calf", form.type to form.customName)
            assertEquals("", form.input)
        }
    }

    @Test
    fun `editing prefills the value, keeps the time on the same day and moves it to noon on another`() = runTest(
        dispatcher
    ) {
        val repo = FakeBodyMeasurementRepository(waist, now = NOW)
        val vm = vm(repo)
        vm.uiState.test {
            awaitItem()
            vm.onEvent(MeasurementsIntent.OpenEdit(waist[1]))
            val opened = expectMostRecentItem()
            assertEquals(
                "82.5".replace('.', es.sebas1705.haltera.core.units.Numbers.decimalSeparator()),
                opened.form.input
            )
            assertEquals(utc(2026, 10, 13, hour = 0), opened.form.dateUtcMillis)
            // Saving untouched keeps everything.
            vm.onEvent(MeasurementsIntent.Save)
            expectMostRecentItem()
            assertEquals(waist[1], repo.rows.value.single { it.id == "w2" })

            vm.onEvent(MeasurementsIntent.OpenEdit(waist[1]))
            vm.onEvent(MeasurementsIntent.InputChanged("83"))
            vm.onEvent(MeasurementsIntent.DateChanged(utc(2026, 10, 1, hour = 0)))
            vm.onEvent(MeasurementsIntent.Save)
            expectMostRecentItem()
            val moved = repo.rows.value.single { it.id == "w2" }
            assertEquals(830L to utc(2026, 10, 1, hour = 12), moved.value to moved.measuredAt)
        }
    }

    @Test
    fun `no future dates - a later day is clamped to today and saved now`() = runTest(dispatcher) {
        val repo = FakeBodyMeasurementRepository(now = NOW)
        val vm = vm(repo)
        vm.uiState.test {
            awaitItem()
            vm.onEvent(MeasurementsIntent.OpenAdd)
            assertEquals(utc(2026, 10, 14, hour = 0), expectMostRecentItem().form.maxDateUtcMillis)
            vm.onEvent(MeasurementsIntent.DateChanged(utc(2026, 10, 20, hour = 0)))
            assertEquals(utc(2026, 10, 14, hour = 0), vm.uiState.value.form.dateUtcMillis) // clamped: nothing changed
            vm.onEvent(MeasurementsIntent.InputChanged("80"))
            vm.onEvent(MeasurementsIntent.Save)
            expectMostRecentItem()
            assertEquals(NOW, repo.rows.value.single().measuredAt)
        }
    }

    @Test
    fun `a past date is saved at noon of that day in the device time zone`() = runTest(dispatcher) {
        val madrid = TimeZone.of("Europe/Madrid")
        val repo = FakeBodyMeasurementRepository(now = NOW)
        // 23:30 UTC on Oct 14 is already Oct 15 in Madrid: "today" follows the device.
        val late = utc(2026, 10, 14, hour = 23, minute = 30)
        val vm = vm(repo, clock = ProgressClock(nowMillis = { late }, timeZone = madrid))
        vm.uiState.test {
            awaitItem()
            vm.onEvent(MeasurementsIntent.OpenAdd)
            assertEquals(utc(2026, 10, 15, hour = 0), expectMostRecentItem().form.dateUtcMillis)
            vm.onEvent(MeasurementsIntent.DateChanged(utc(2026, 10, 10, hour = 0)))
            vm.onEvent(MeasurementsIntent.InputChanged("80"))
            vm.onEvent(MeasurementsIntent.Save)
            expectMostRecentItem()
            // Noon in Madrid (UTC+2 in October) is 10:00 UTC.
            assertEquals(utc(2026, 10, 10, hour = 10), repo.rows.value.single().measuredAt)
        }
    }

    @Test
    fun `deleting from the list or the dialog removes the entry and closes the dialog`() = runTest(dispatcher) {
        val repo = FakeBodyMeasurementRepository(waist, now = NOW)
        val vm = vm(repo)
        vm.uiState.test {
            awaitItem()
            vm.onEvent(MeasurementsIntent.OpenEdit(waist[0]))
            vm.onEvent(MeasurementsIntent.Delete("w1"))
            val state = expectMostRecentItem()
            assertNull(state.dialog)
            assertEquals(listOf("w2"), state.selected?.entries?.map { it.id })
            vm.onEvent(MeasurementsIntent.Delete("w2"))
            assertTrue(expectMostRecentItem().series.isEmpty())
        }
    }

    @Test
    fun `long names and notes are cut while typing, and dismiss closes without saving`() = runTest(dispatcher) {
        val repo = FakeBodyMeasurementRepository(now = NOW)
        val vm = vm(repo)
        vm.uiState.test {
            awaitItem()
            vm.onEvent(MeasurementsIntent.OpenAdd)
            vm.onEvent(MeasurementsIntent.CustomNameChanged("n".repeat(100)))
            vm.onEvent(MeasurementsIntent.NotesChanged("x".repeat(500)))
            val form = expectMostRecentItem().form
            assertEquals(40, form.customName.length)
            assertEquals(MeasurementsViewModel.MAX_NOTES, form.notes.length)
            vm.onEvent(MeasurementsIntent.Dismiss)
            assertNull(expectMostRecentItem().dialog)
            assertTrue(repo.rows.value.isEmpty())
        }
    }
}
