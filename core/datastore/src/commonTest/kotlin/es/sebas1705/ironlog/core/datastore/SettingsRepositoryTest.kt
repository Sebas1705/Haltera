package es.sebas1705.ironlog.core.datastore

import com.russhwolf.settings.MapSettings
import es.sebas1705.ironlog.core.units.PlateCalculator
import es.sebas1705.ironlog.core.units.WeightUnit
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
}
