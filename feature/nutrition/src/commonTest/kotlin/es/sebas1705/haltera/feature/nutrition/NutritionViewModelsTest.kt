package es.sebas1705.haltera.feature.nutrition

import es.sebas1705.haltera.data.models.ActivityLevel
import es.sebas1705.haltera.data.models.BarcodeLookup
import es.sebas1705.haltera.data.models.BodyWeightEntry
import es.sebas1705.haltera.data.models.MacroMode
import es.sebas1705.haltera.data.models.Meal
import es.sebas1705.haltera.data.models.Objective
import es.sebas1705.haltera.data.models.Sex
import es.sebas1705.haltera.feature.nutrition.model.AmountMode
import es.sebas1705.haltera.feature.nutrition.model.AppLanguage
import es.sebas1705.haltera.feature.nutrition.scan.BarcodeScanner
import es.sebas1705.haltera.feature.nutrition.ui.day.CopyTarget
import es.sebas1705.haltera.feature.nutrition.ui.day.DayIntent
import es.sebas1705.haltera.feature.nutrition.ui.day.DayViewModel
import es.sebas1705.haltera.feature.nutrition.ui.detail.FoodDetailArgs
import es.sebas1705.haltera.feature.nutrition.ui.detail.FoodDetailIntent
import es.sebas1705.haltera.feature.nutrition.ui.detail.FoodDetailViewModel
import es.sebas1705.haltera.feature.nutrition.ui.editor.FoodEditorIntent
import es.sebas1705.haltera.feature.nutrition.ui.editor.FoodEditorViewModel
import es.sebas1705.haltera.feature.nutrition.ui.editor.FoodField
import es.sebas1705.haltera.feature.nutrition.ui.goals.GoalsIntent
import es.sebas1705.haltera.feature.nutrition.ui.goals.GoalsViewModel
import es.sebas1705.haltera.feature.nutrition.ui.goals.Macro
import es.sebas1705.haltera.feature.nutrition.ui.search.FoodSearchIntent
import es.sebas1705.haltera.feature.nutrition.ui.search.FoodSearchViewModel
import es.sebas1705.haltera.feature.nutrition.ui.search.ScanProblem
import es.sebas1705.haltera.feature.nutrition.ui.stats.StatsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NutritionViewModelsTest {
    private val repo = FakeNutritionRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the day view switches dates and copies from another day`() = runTest {
        repo.addEntry(DAY, Meal.BREAKFAST, "oats", 50.0)
        val vm = DayViewModel(repo, FixedToday)
        assertEquals(190.0, vm.uiState.value.day.totals.kcal, 1e-9)

        vm.onEvent(DayIntent.NextDay)
        assertEquals(LocalDate(2026, 10, 16), vm.uiState.value.date)
        assertTrue(vm.uiState.value.day.entries.isEmpty())

        vm.onEvent(DayIntent.StartCopy(CopyTarget.WholeDay))
        vm.onEvent(DayIntent.CopyFrom(DAY))
        assertEquals(listOf("day 2026-10-15 -> 2026-10-16"), repo.copies)
        assertEquals(1, vm.uiState.value.copiedCount)
        assertNull(vm.uiState.value.copying)

        vm.onEvent(DayIntent.GoToToday)
        assertEquals(DAY, vm.uiState.value.date)
    }

    @Test
    fun `the food detail starts in servings when the food has one and logs grams`() = runTest {
        val vm = FoodDetailViewModel(FoodDetailArgs("milk", DAY, Meal.BREAKFAST), repo)
        advanceUntilIdle()
        assertEquals(AmountMode.SERVINGS, vm.uiState.value.mode)
        assertEquals(250.0, vm.uiState.value.grams)

        vm.onEvent(FoodDetailIntent.AmountChanged("1,5"))
        assertEquals(375.0, vm.uiState.value.grams)
        vm.onEvent(FoodDetailIntent.ModeChanged(AmountMode.GRAMS))
        assertEquals("375", vm.uiState.value.amountText)

        vm.onEvent(FoodDetailIntent.MealChanged(Meal.SNACK))
        vm.onEvent(FoodDetailIntent.Save)
        val entry = repo.entries.value.single()
        assertEquals(375.0, entry.grams)
        assertNull(entry.servings)
        assertEquals(Meal.SNACK, entry.meal)
        assertTrue(vm.uiState.value.finished)
    }

    @Test
    fun `editing an entry keeps its servings and can move it`() = runTest {
        val id = repo.addEntry(DAY, Meal.LUNCH, "milk", 500.0, servings = 2.0)
        val vm = FoodDetailViewModel(FoodDetailArgs("milk", DAY, Meal.LUNCH, id), repo)
        advanceUntilIdle()
        assertEquals("2", vm.uiState.value.amountText)
        vm.onEvent(FoodDetailIntent.AmountChanged("1"))
        vm.onEvent(FoodDetailIntent.MealChanged(Meal.DINNER))
        vm.onEvent(FoodDetailIntent.Save)
        val entry = repo.entries.value.single()
        assertEquals(250.0 to 1.0, entry.grams to entry.servings)
        assertEquals(Meal.DINNER, entry.meal)
    }

    @Test
    fun `a custom food needs a name and the four main values`() = runTest {
        val vm = FoodEditorViewModel(null, "8410000000017", repo)
        vm.onEvent(FoodEditorIntent.Save)
        assertTrue(vm.uiState.value.showErrors)
        assertEquals(
            setOf(FoodField.NAME, FoodField.KCAL, FoodField.PROTEIN, FoodField.CARBS, FoodField.FAT),
            vm.uiState.value.errors,
        )
        listOf(
            FoodField.NAME to "Protein bar",
            FoodField.KCAL to "350",
            FoodField.PROTEIN to "30",
            FoodField.CARBS to "35,5",
            FoodField.FAT to "9"
        )
            .forEach { (field, text) -> vm.onEvent(FoodEditorIntent.Changed(field, text)) }
        vm.onEvent(FoodEditorIntent.Changed(FoodField.SERVING_NAME, "bar"))
        assertEquals(setOf(FoodField.SERVING_GRAMS), vm.uiState.value.errors)
        vm.onEvent(FoodEditorIntent.Changed(FoodField.SERVING_GRAMS, "60"))
        vm.onEvent(FoodEditorIntent.Save)

        val saved = assertNotNull(vm.uiState.value.finished?.foodId)
        val food = repo.foods.value.getValue(saved)
        assertEquals(35.5, food.per100.carbs)
        assertEquals("8410000000017", food.barcode)
        assertEquals(60.0, food.servingGrams)
    }

    @Test
    fun `goals use the calculator and convert macros between grams and percent`() = runTest {
        val weights = FakeBodyWeightRepository(listOf(BodyWeightEntry("w", 1, 80_000, "")))
        val vm = GoalsViewModel(repo, weights, FixedToday)
        advanceUntilIdle()
        assertNull(vm.uiState.value.suggestion)
        vm.onEvent(GoalsIntent.SexChanged(Sex.MALE))
        vm.onEvent(GoalsIntent.BirthYearChanged("1996"))
        vm.onEvent(GoalsIntent.HeightChanged("180"))
        vm.onEvent(GoalsIntent.ActivityChanged(ActivityLevel.MODERATE))
        vm.onEvent(GoalsIntent.ObjectiveChanged(Objective.LOSE))
        assertEquals(2_210, vm.uiState.value.suggestion?.kcal)

        vm.onEvent(GoalsIntent.ApplySuggestion)
        assertEquals("2210", vm.uiState.value.kcalText)
        assertEquals("160", vm.uiState.value.macroText(Macro.PROTEIN))
        vm.onEvent(GoalsIntent.ModeChanged(MacroMode.PERCENT))
        assertEquals("29", vm.uiState.value.macroText(Macro.PROTEIN)) // 640 / 2210
        assertEquals(100, vm.uiState.value.percentSum)

        vm.onEvent(GoalsIntent.Save)
        val goals = repo.goals.value
        assertEquals(2_210, goals.kcal)
        assertEquals(MacroMode.PERCENT, goals.macroMode)
        assertEquals(160, goals.proteinG) // 29 % back to grams
        assertEquals(Sex.MALE, goals.profile.sex)
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun `a scanned product opens it, a missing one offers to create it`() = runTest {
        val scanner = object : BarcodeScanner {
            override val isAvailable = true
            override suspend fun scan() = "8410000000017"
        }
        val vm = FoodSearchViewModel(repo, AppLanguage { "es" }, scanner)
        repo.lookup = BarcodeLookup.Found(MILK)
        vm.onEvent(FoodSearchIntent.Scan)
        assertEquals("milk", vm.uiState.value.openFoodId)

        vm.onEvent(FoodSearchIntent.FoodOpened)
        repo.lookup = BarcodeLookup.NotFound
        vm.onEvent(FoodSearchIntent.Scan)
        assertEquals(ScanProblem.NotFound("8410000000017"), vm.uiState.value.scanProblem)
    }

    @Test
    fun `search waits for typing to settle and finds Spanish names`() = runTest {
        val vm = FoodSearchViewModel(
            repo,
            AppLanguage { "es" },
            es.sebas1705.haltera.feature.nutrition.scan.NoBarcodeScanner
        )
        vm.onEvent(FoodSearchIntent.QueryChanged("lec"))
        vm.onEvent(FoodSearchIntent.QueryChanged("leche"))
        advanceUntilIdle()
        assertEquals(listOf("milk"), vm.uiState.value.results.map { it.id })
        assertEquals(listOf("milk"), vm.uiState.value.listed.map { it.id })
    }

    @Test
    fun `statistics cover the logged days of the range`() = runTest {
        repo.goals.value = repo.goals.value.copy(kcal = 2_000)
        repo.addEntry(DAY, Meal.LUNCH, "oats", 500.0) // 1900 kcal: within 10 %
        repo.addEntry(LocalDate(2026, 10, 10), Meal.LUNCH, "oats", 100.0) // 380 kcal
        val vm = StatsViewModel(
            repo,
            FakeBodyWeightRepository(listOf(BodyWeightEntry("w", 1, 80_000, ""))),
            FixedToday,
            TimeZone.UTC
        )
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(2, state.daily.size)
        assertEquals(1, state.adherence?.daysWithin)
        assertEquals((1_900.0 + 380.0) / 2, state.average!!.kcal, 1e-9)
        assertEquals((65.0 + 13.0) / 2 / 80.0, state.proteinPerKg!!, 1e-9)
    }
}
