package es.sebas1705.ironlog.data.remote

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.apps

/** Whether this build has a Firebase config; the platform provides it when it starts Koin. */
data class FirebaseConfig(val configured: Boolean)

/**
 * True when a default Firebase app exists: on Android the google-services plugin initialises it
 * (pass the application context), on iOS `FirebaseApp.configure()` in Swift.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
fun isFirebaseConfigured(platformContext: Any? = null): Boolean = try {
    Firebase.apps(platformContext).isNotEmpty()
} catch (e: Exception) {
    false
}
