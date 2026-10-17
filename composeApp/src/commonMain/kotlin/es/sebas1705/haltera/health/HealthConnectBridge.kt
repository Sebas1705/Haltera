package es.sebas1705.haltera.health

import es.sebas1705.haltera.core.datastore.HealthDataType

/**
 * Platform screens of the health integration. Android: the Health Connect permission screen, its Google Play
 * page (Android 9–13) and its app-permission settings. iOS: nothing yet (HealthKit is a follow-up).
 */
interface HealthConnectBridge {
    /** Shows the permission screen for [types]; returns when the user closes it (granted or not). */
    suspend fun requestPermissions(types: Set<HealthDataType>)

    /** Opens Health Connect in Google Play to install or update it. */
    fun openInstallPage()

    /** Opens Health Connect's settings for Haltera, where permissions are managed or revoked. */
    fun openManagePermissions()
}

/** No health platform: every action does nothing. */
object NoHealthConnectBridge : HealthConnectBridge {
    override suspend fun requestPermissions(types: Set<HealthDataType>) = Unit

    override fun openInstallPage() = Unit

    override fun openManagePermissions() = Unit
}
