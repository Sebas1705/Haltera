package es.sebas1705.haltera.core.datastore

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrashReportsStoreTest {
    private val settings = MapSettings()

    @Test
    fun `a fresh install sends crash reports`() {
        assertTrue(SettingsCrashReportsStore(settings).enabled.value)
    }

    @Test
    fun `settings written by a version without the switch read as on`() {
        settings.putString("settings.unit", "KG")
        settings.putBoolean("cloud.enabled", false)
        assertTrue(SettingsCrashReportsStore(settings).enabled.value)
    }

    @Test
    fun `turning it off survives a restart and can be undone`() {
        val store = SettingsCrashReportsStore(settings)
        store.setEnabled(false)
        assertFalse(store.enabled.value)
        assertFalse(SettingsCrashReportsStore(settings).enabled.value)

        SettingsCrashReportsStore(settings).setEnabled(true)
        assertTrue(SettingsCrashReportsStore(settings).enabled.value)
    }

    @Test
    fun `the switch is independent of the cloud copy`() {
        SettingsCrashReportsStore(settings).setEnabled(false)
        SettingsCloudBackupStore(settings).setEnabled(true)
        assertFalse(SettingsCrashReportsStore(settings).enabled.value)
        assertTrue(SettingsCloudBackupStore(settings).enabled.value)
    }
}
