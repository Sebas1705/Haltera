package es.sebas1705.haltera.core.datastore

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HealthSettingsStoreTest {
    private val settings = MapSettings()

    @Test
    fun `a fresh install is not connected, with the default types and an 8000 steps goal`() {
        val s = SettingsHealthSettingsStore(settings).settings.value
        assertFalse(s.connected)
        assertEquals(HealthDataType.DEFAULT_ENABLED, s.enabled)
        assertEquals(8_000, s.stepGoal)
        assertEquals(0L, s.lastRefreshAt)
        assertEquals(0L, s.exportSince)
    }

    @Test
    fun `the defaults read nothing sensitive beyond what the integration is for`() {
        assertFalse(HealthDataType.RESTING_HEART_RATE in HealthDataType.DEFAULT_ENABLED)
        assertFalse(HealthDataType.SLEEP in HealthDataType.DEFAULT_ENABLED)
        assertFalse(HealthDataType.WEIGHT_EXPORT in HealthDataType.DEFAULT_ENABLED)
        assertTrue(HealthDataType.STEPS in HealthDataType.DEFAULT_ENABLED)
    }

    @Test
    fun `every field survives a restart`() {
        SettingsHealthSettingsStore(settings).update {
            it.copy(
                connected = true,
                enabled = setOf(HealthDataType.SLEEP, HealthDataType.WEIGHT_EXPORT),
                stepGoal = 12_000,
                lastRefreshAt = 5,
                weightWatermark = 6,
                exportSince = 7,
            )
        }
        val s = SettingsHealthSettingsStore(settings).settings.value
        assertEquals(
            HealthSettings(true, setOf(HealthDataType.SLEEP, HealthDataType.WEIGHT_EXPORT), 12_000, 5, 6, 7),
            s,
        )
    }

    @Test
    fun `an empty set of types is kept, not replaced by the defaults`() {
        SettingsHealthSettingsStore(settings).update { it.copy(enabled = emptySet()) }
        assertEquals(emptySet(), SettingsHealthSettingsStore(settings).settings.value.enabled)
    }

    @Test
    fun `unknown type names from a newer version are ignored`() {
        settings.putString("health.enabled", "STEPS,BLOOD_GLUCOSE")
        assertEquals(setOf(HealthDataType.STEPS), SettingsHealthSettingsStore(settings).settings.value.enabled)
    }

    @Test
    fun `the steps goal is clamped to a plausible range`() {
        val store = SettingsHealthSettingsStore(settings)
        store.setStepGoal(10)
        assertEquals(HealthSettings.MIN_STEP_GOAL, store.settings.value.stepGoal)
        store.setStepGoal(1_000_000)
        assertEquals(HealthSettings.MAX_STEP_GOAL, store.settings.value.stepGoal)
        store.setStepGoal(9_500)
        assertEquals(9_500, store.settings.value.stepGoal)
        settings.putInt("health.stepGoal", -3)
        assertEquals(HealthSettings.MIN_STEP_GOAL, SettingsHealthSettingsStore(settings).settings.value.stepGoal)
    }
}
