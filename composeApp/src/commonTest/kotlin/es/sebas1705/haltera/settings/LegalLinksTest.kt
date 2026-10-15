package es.sebas1705.haltera.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegalLinksTest {
    @Test
    fun `Spanish gets the Spanish pages`() {
        assertEquals(LegalLinks.PRIVACY_ES, LegalLinks.privacyPolicy("es"))
        assertEquals(LegalLinks.DELETE_ACCOUNT_ES, LegalLinks.deleteAccount("es"))
        assertEquals(LegalLinks.PRIVACY_ES, LegalLinks.privacyPolicy("es-ES"))
    }

    @Test
    fun `every other language gets the English pages`() {
        assertEquals(LegalLinks.PRIVACY_EN, LegalLinks.privacyPolicy("en"))
        assertEquals(LegalLinks.DELETE_ACCOUNT_EN, LegalLinks.deleteAccount("fr"))
    }

    @Test
    fun `links are https pages of the project site`() {
        listOf(
            LegalLinks.PRIVACY_EN,
            LegalLinks.PRIVACY_ES,
            LegalLinks.DELETE_ACCOUNT_EN,
            LegalLinks.DELETE_ACCOUNT_ES,
        ).forEach { assertTrue(it.startsWith("https://haltera.web.app/"), it) }
    }
}
