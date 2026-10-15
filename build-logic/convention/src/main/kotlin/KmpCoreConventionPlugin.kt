import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

/** Core modules: same as library but may add compose for :core:ui. */
class KmpCoreConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "haltera.kmp.library")
    }
}
