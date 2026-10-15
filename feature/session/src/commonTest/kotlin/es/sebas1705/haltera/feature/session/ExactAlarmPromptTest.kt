package es.sebas1705.haltera.feature.session

import es.sebas1705.haltera.feature.session.platform.ExactAlarmAccess
import es.sebas1705.haltera.feature.session.platform.ExactAlarmPrompt
import es.sebas1705.haltera.feature.session.platform.ExactAlarmPromptStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExactAlarmPromptTest {
    private class Access(var missing: Boolean) : ExactAlarmAccess {
        var opened = 0
        override fun isMissing() = missing
        override fun openSettings() {
            opened++
        }
    }

    private class Store(override var handled: Boolean = false) : ExactAlarmPromptStore

    @Test
    fun `shown when the permission is missing and nothing was answered yet`() {
        val prompt = ExactAlarmPrompt(Access(missing = true), Store())

        assertTrue(prompt.shouldShow())
        assertTrue(prompt.shouldShow()) // Still pending until the user answers.
    }

    @Test
    fun `not now is remembered and it never comes back`() {
        val store = Store()
        val access = Access(missing = true)
        val prompt = ExactAlarmPrompt(access, store)

        prompt.notNow()

        assertFalse(prompt.shouldShow())
        // A new process reads the same store.
        assertFalse(ExactAlarmPrompt(access, store).shouldShow())
        assertEquals(0, access.opened)
    }

    @Test
    fun `opening the system screen counts as the one time`() {
        val store = Store()
        val access = Access(missing = true)
        val prompt = ExactAlarmPrompt(access, store)

        prompt.openSettings()

        assertEquals(1, access.opened)
        assertFalse(prompt.shouldShow())
        assertTrue(store.handled)
    }

    @Test
    fun `not shown when already granted or not applicable, and then never later`() {
        val store = Store()
        val access = Access(missing = false)
        val prompt = ExactAlarmPrompt(access, store)

        assertFalse(prompt.shouldShow())
        access.missing = true // Revoked afterwards: still no nagging.
        assertFalse(prompt.shouldShow())
    }
}
