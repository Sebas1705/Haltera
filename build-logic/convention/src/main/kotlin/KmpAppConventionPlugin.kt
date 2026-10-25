import ext.configureAndroidApp
import ext.configureKmp
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

/**
 * Application-module convention for composeApp.
 * Applies: android application + KMP + Compose + serialization.
 */
class KmpAppConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "org.jetbrains.kotlin.multiplatform")
        apply(plugin = "com.android.application")
        apply(plugin = "org.jetbrains.compose")
        apply(plugin = "org.jetbrains.kotlin.plugin.compose")
        apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
        apply(plugin = "haltera.detekt")

        configureKmp()
        configureAndroidApp(namespace = "es.sebas1705.haltera", applicationId = "es.sebas1705.haltera")
    }
}
