package es.sebas1705.ironlog.core.security

import platform.Foundation.NSUserDefaults

actual class SecureStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getString(key: String, defaultValue: String) =
        defaults.stringForKey(key) ?: defaultValue
    actual fun putString(key: String, value: String) =
        defaults.setObject(value, forKey = key)
    actual fun remove(key: String)  = defaults.removeObjectForKey(key)
    actual fun clear()              = defaults.dictionaryRepresentation().keys.forEach { remove(it as String) }
    actual fun hasKey(key: String)  = defaults.objectForKey(key) != null
}
