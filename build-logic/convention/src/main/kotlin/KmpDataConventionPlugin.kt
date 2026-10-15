import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

/**
 * Data modules: library + serialization.
 * The SQLDelight plugin is applied per module, only where a database is needed.
 */
class KmpDataConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "haltera.kmp.library")
    }
}
