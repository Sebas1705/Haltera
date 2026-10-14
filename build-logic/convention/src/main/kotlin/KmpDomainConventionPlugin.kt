import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

/** Domain/use-case modules: pure Kotlin, no platform SDKs. */
class KmpDomainConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "ironlog.kmp.library")
    }
}
