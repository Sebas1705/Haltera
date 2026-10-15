package es.sebas1705.haltera.settings

/**
 * The public web pages the About screen links to (Google Play requires a privacy policy and a way to
 * request account deletion outside the app). The only place these URLs are written.
 */
object LegalLinks {
    const val PRIVACY_EN = "https://haltera.web.app/privacy"
    const val PRIVACY_ES = "https://haltera.web.app/privacidad"
    const val DELETE_ACCOUNT_EN = "https://haltera.web.app/delete-account"
    const val DELETE_ACCOUNT_ES = "https://haltera.web.app/borrar-cuenta"

    /** The privacy policy in the app language (`es` or anything else: English). */
    fun privacyPolicy(language: String): String = if (isSpanish(language)) PRIVACY_ES else PRIVACY_EN

    /** The "delete my account and data" page in the app language. */
    fun deleteAccount(language: String): String = if (isSpanish(language)) DELETE_ACCOUNT_ES else DELETE_ACCOUNT_EN

    private fun isSpanish(language: String) = language.lowercase().startsWith("es")
}

/** Version shown in About: the platform's version name and build number (Android versionCode, iOS CFBundleVersion). */
data class AppInfo(val versionName: String, val versionCode: String)
