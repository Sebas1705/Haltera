package es.sebas1705.ironlog.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppPreferences(private val dataStore: DataStore<Preferences>) {

    // ── Theme ─────────────────────────────────────────────────────────────────
    val isDarkMode: Flow<Boolean> = dataStore.data.map { it[Keys.DARK_MODE] ?: false }
    suspend fun setDarkMode(value: Boolean) { dataStore.edit { it[Keys.DARK_MODE] = value } }

    // ── Locale ────────────────────────────────────────────────────────────────
    val language: Flow<String> = dataStore.data.map { it[Keys.LANGUAGE] ?: "en" }
    suspend fun setLanguage(code: String) { dataStore.edit { it[Keys.LANGUAGE] = code } }

    // ── Onboarding ────────────────────────────────────────────────────────────
    val onboardingDone: Flow<Boolean> = dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }
    suspend fun setOnboardingDone()    { dataStore.edit { it[Keys.ONBOARDING_DONE] = true } }

    private object Keys {
        val DARK_MODE       = booleanPreferencesKey("dark_mode")
        val LANGUAGE        = stringPreferencesKey("language")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }
}

expect fun createDataStore(): DataStore<Preferences>
