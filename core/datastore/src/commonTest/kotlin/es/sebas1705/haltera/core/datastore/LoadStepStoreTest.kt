package es.sebas1705.haltera.core.datastore

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoadStepStoreTest {
    private val settings = MapSettings()

    @Test
    fun `steps are remembered per variant and per mode across instances`() {
        val store = SettingsLoadStepStore(settings)
        store.setStep("lat-pulldown.wide-grip", step = 1_250)
        store.setStep("lat-pulldown.wide-grip", plates = true, step = 2)

        val reloaded = SettingsLoadStepStore(settings)
        assertEquals(1_250L, reloaded.step("lat-pulldown.wide-grip"))
        assertEquals(2L, reloaded.step("lat-pulldown.wide-grip", plates = true))
        assertNull(reloaded.step("bench-press.barbell"))
        assertEquals(1_250L, settings.getLongOrNull("loadStep.lat-pulldown.wide-grip"))
    }

    @Test
    fun `clearing or a non-positive step goes back to the default`() {
        val store = SettingsLoadStepStore(settings)
        store.setStep("squat.barbell", step = 5_000)
        store.setStep("squat.barbell", step = null)
        assertNull(store.step("squat.barbell"))
        store.setStep("squat.barbell", step = 0)
        assertNull(store.step("squat.barbell"))
    }

    @Test
    fun `steps never touch the synced user settings`() {
        SettingsLoadStepStore(settings).setStep("squat.barbell", step = 5_000)
        assertEquals(UserSettings(), KeyValueSettingsRepository(settings).settings.value)
    }
}
