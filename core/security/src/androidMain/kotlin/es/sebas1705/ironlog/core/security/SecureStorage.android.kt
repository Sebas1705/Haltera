package es.sebas1705.ironlog.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

lateinit var appContext: Context

actual class SecureStorage {
    private val prefs by lazy {
        val master = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            appContext, "secure_prefs", master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    actual fun getString(key: String, defaultValue: String) =
        prefs.getString(key, defaultValue) ?: defaultValue
    actual fun putString(key: String, value: String) =
        prefs.edit().putString(key, value).apply()
    actual fun remove(key: String) = prefs.edit().remove(key).apply()
    actual fun clear()             = prefs.edit().clear().apply()
    actual fun hasKey(key: String) = prefs.contains(key)
}
