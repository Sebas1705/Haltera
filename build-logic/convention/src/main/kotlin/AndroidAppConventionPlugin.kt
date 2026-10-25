import ext.configureAndroidApp
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

/**
 * Convention for androidApp, the pure-Android application module (`com.android.application` with AGP's
 * built-in Kotlin and the new DSL; no KMP plugin). It holds the manifest, launcher resources, signing and
 * the Android entry points, and depends on composeApp, the KMP library with the shared UI.
 * Applies: android application + Compose compiler + detekt.
 */
class AndroidAppConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "com.android.application")
        apply(plugin = "org.jetbrains.kotlin.plugin.compose")
        apply(plugin = "haltera.detekt")

        configureAndroidApp(namespace = "es.sebas1705.haltera", applicationId = "es.sebas1705.haltera")
    }
}
