import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

/**
 * Data modules: library + KSP (for SQLDelight, etc.) + serialization.
 * SQLDelight plugin is applied per-module only where a DB is actually needed.
 */
class KmpDataConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "ironlog.kmp.library")
        apply(plugin = "com.google.devtools.ksp")
    }
}
