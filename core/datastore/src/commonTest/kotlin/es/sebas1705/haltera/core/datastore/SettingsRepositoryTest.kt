package es.sebas1705.haltera.core.datastore

import com.russhwolf.settings.MapSettings
import es.sebas1705.haltera.core.units.PlateCalculator
import es.sebas1705.haltera.core.units.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SettingsRepositoryTest {
    private val store = MapSettings()

    @Test
    fun `defaults are kg with a 20 kg bar`() {
        val settings = KeyValueSettingsRepository(store).settings.value
        assertEquals(UserSettings(), settings)
        assertEquals(20_000L, settings.barWeightGrams)
    }

    @Test
    fun `changes persist across instances`() {
        val repo = KeyValueSettingsRepository(store)
        repo.setBarWeight(15_000)
        repo.setAvailablePlates(listOf(5_000, 20_000, 20_000, 0, 1_250))
        repo.setKeepScreenOn(false)
        repo.setRestSound(false)

        val reloaded = KeyValueSettingsRepository(store).settings.value
        assertEquals(15_000L, reloaded.barWeightGrams)
        assertEquals(listOf(20_000L, 5_000L, 1_250L), reloaded.availablePlatesGrams)
        assertFalse(reloaded.keepScreenOn)
        assertFalse(reloaded.restSound)
    }

    @Test
    fun `switching unit resets the equipment unless asked not to`() {
        val repo = KeyValueSettingsRepository(store)
        repo.setKeepScreenOn(false)
        repo.setUnit(WeightUnit.LB)
        assertEquals(UserSettings.DEFAULT_BAR_LB, repo.settings.value.barWeightGrams)
        assertEquals(PlateCalculator.LB_PLATES, repo.settings.value.availablePlatesGrams)
        assertFalse(repo.settings.value.keepScreenOn)

        repo.setBarWeight(10_000)
        repo.setUnit(WeightUnit.KG, resetEquipment = false)
        assertEquals(WeightUnit.KG, repo.settings.value.unit)
        assertEquals(10_000L, repo.settings.value.barWeightGrams)
    }

    @Test
    fun `corrupt stored values fall back to defaults`() {
        store.putString("settings.unit", "stone")
        store.putString("settings.platesGrams", "a,b")
        val settings = KeyValueSettingsRepository(store).settings.value
        assertEquals(WeightUnit.KG, settings.unit)
        assertEquals(PlateCalculator.KG_PLATES, settings.availablePlatesGrams)
    }

    @Test
    fun `nutrition goals persist, clear back to null and survive a unit switch`() {
        val repo = KeyValueSettingsRepository(store)
        val goals = NutritionSettings(
            kcal = 2_400, proteinG = 160, carbsG = 250, fatG = 80, macroMode = "PERCENT", sex = "FEMALE",
            birthYear = 1995, heightCm = 168, activity = "ACTIVE", objective = "LOSE",
        )
        repo.setNutrition(goals)
        assertEquals(goals, KeyValueSettingsRepository(store).settings.value.nutrition)

        repo.setUnit(WeightUnit.LB)
        assertEquals(goals, repo.settings.value.nutrition)

        repo.setNutrition(goals.copy(kcal = null, sex = null))
        val reloaded = KeyValueSettingsRepository(store).settings.value.nutrition
        assertEquals(null, reloaded.kcal)
        assertEquals(null, reloaded.sex)
        assertEquals(160, reloaded.proteinG)
    }

    @Test
    fun `the name for shared routines starts unset, persists as typed, survives a unit switch and resets`() {
        val repo = KeyValueSettingsRepository(store)
        assertEquals(null, repo.settings.value.shareName)
        repo.setShareName("Ana ")
        assertEquals("Ana ", KeyValueSettingsRepository(store).settings.value.shareName)
        repo.setShareName("x".repeat(100))
        assertEquals(MAX_SHARE_NAME, repo.settings.value.shareName?.length)
        repo.setShareName("")
        assertEquals("", KeyValueSettingsRepository(store).settings.value.shareName)
        repo.setShareName("Ana")
        repo.setUnit(WeightUnit.LB)
        assertEquals("Ana", repo.settings.value.shareName)
        repo.reset()
        assertEquals(null, KeyValueSettingsRepository(store).settings.value.shareName)
    }
}
