import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

/**
 * Feature-module convention.
 * Extends KmpLibrary with Compose Multiplatform + Compose compiler.
 *
 * Each feature build.gradle.kts still declares its own dependencies
 * (navigation, koin-compose-viewmodel, lifecycle, etc.) via the catalog.
 */
class KmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "ironlog.kmp.library")
        apply(plugin = "org.jetbrains.compose")
        apply(plugin = "org.jetbrains.kotlin.plugin.compose")
    }
}
