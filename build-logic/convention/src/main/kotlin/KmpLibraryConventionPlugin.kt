import ext.configureAndroidLibrary
import ext.configureKmp
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

/**
 * Base KMP library convention.
 * Applies: kotlin.multiplatform, com.android.library, kotlin.serialization, detekt.
 * Configures: Android + iOS targets, jvmTarget = 17, minSdk = 24.
 *
 * Use this on every non-app module. Specialised conventions (feature, data, etc.)
 * build on top by applying additional plugins or adding dependencies.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "org.jetbrains.kotlin.multiplatform")
        apply(plugin = "com.android.library")
        apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
        apply(plugin = "ironlog.detekt")

        val namespace = "es.sebas1705.ironlog" +
            project.path.replace(":", ".").let { if (it.startsWith(".")) it else ".$it" }
        configureAndroidLibrary(namespace)
        configureKmp()
    }
}
