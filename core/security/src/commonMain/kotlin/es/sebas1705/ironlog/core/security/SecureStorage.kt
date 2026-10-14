package es.sebas1705.ironlog.core.security

expect class SecureStorage {
    fun getString(key: String, defaultValue: String = ""): String
    fun putString(key: String, value: String)
    fun remove(key: String)
    fun clear()
    fun hasKey(key: String): Boolean
}
