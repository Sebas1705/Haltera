package es.sebas1705.haltera.core.datastore

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloudBackupStoreTest {
    private val settings = MapSettings()

    @Test
    fun `a fresh install is off and undecided`() {
        val store = SettingsCloudBackupStore(settings)
        assertEquals(CloudBackupState(), store.state.value)
        assertFalse(store.enabled.value)
    }

    @Test
    fun `settings written by a version without the flag read as off and undecided`() {
        // Keys of an older install: unit, bar, nutrition goals. None of them is a cloud key.
        settings.putString("settings.unit", "KG")
        settings.putInt("nutrition.heightCm", 180)
        assertEquals(CloudBackupState(), SettingsCloudBackupStore(settings).state.value)
        assertEquals(UserSettings().nutrition.heightCm, null)
    }

    @Test
    fun `the choice survives a restart and answers the question`() {
        SettingsCloudBackupStore(settings).setEnabled(true)
        val reloaded = SettingsCloudBackupStore(settings)
        assertTrue(reloaded.enabled.value)
        assertTrue(reloaded.state.value.promptHandled)

        reloaded.setEnabled(false)
        val again = SettingsCloudBackupStore(settings)
        assertFalse(again.enabled.value)
        assertTrue(again.state.value.promptHandled)
    }

    @Test
    fun `Not now keeps it off and is remembered`() {
        SettingsCloudBackupStore(settings).dismissPrompt()
        val reloaded = SettingsCloudBackupStore(settings)
        assertFalse(reloaded.enabled.value)
        assertTrue(reloaded.state.value.promptHandled)
    }

    @Test
    fun `an install that was already signed in is migrated to on with a one-time notice`() {
        val store = SettingsCloudBackupStore(settings)
        store.adoptExistingAccount()
        assertEquals(CloudBackupState(enabled = true, promptHandled = true, noticePending = true), store.state.value)

        store.dismissNotice()
        val reloaded = SettingsCloudBackupStore(settings)
        assertEquals(
            CloudBackupState(enabled = true, promptHandled = true, noticePending = false),
            reloaded.state.value
        )
    }

    @Test
    fun `migration never overrides a choice already made`() {
        val off = SettingsCloudBackupStore(settings)
        off.setEnabled(false)
        off.adoptExistingAccount()
        assertEquals(CloudBackupState(enabled = false, promptHandled = true), off.state.value)

        val declined = SettingsCloudBackupStore(MapSettings())
        declined.dismissPrompt()
        declined.adoptExistingAccount()
        assertFalse(declined.enabled.value)
    }

    @Test
    fun `the flag never touches the synced user settings`() {
        SettingsCloudBackupStore(settings).setEnabled(true)
        assertEquals(UserSettings(), KeyValueSettingsRepository(settings).settings.value)
    }
}
