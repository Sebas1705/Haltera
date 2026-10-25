import ext.configureAndroidLibrary
import ext.configureKmp
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

/**
 * Base KMP library convention.
 * Applies: kotlin.multiplatform, com.android.kotlin.multiplatform.library, kotlin.serialization, detekt.
 * Configures: Android + iOS targets, jvmTarget = 17, minSdk = 24, JVM tests in `src/androidHostTest`.
 *
 * Use this on every non-app module. Specialised conventions (feature, data, etc.)
 * build on top by applying additional plugins or adding dependencies.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "org.jetbrains.kotlin.multiplatform")
        apply(plugin = "com.android.kotlin.multiplatform.library")
        apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
        apply(plugin = "haltera.detekt")

        val namespace = "es.sebas1705.haltera" +
            project.path.replace(":", ".").let { if (it.startsWith(".")) it else ".$it" }
        configureKmp()
        configureAndroidLibrary(namespace)
    }
}
