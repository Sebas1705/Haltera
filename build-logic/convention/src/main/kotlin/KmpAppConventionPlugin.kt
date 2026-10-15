import ext.COMPILE_SDK
import ext.MIN_SDK
import ext.TARGET_SDK
import ext.configureKmp
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import com.android.build.api.dsl.ApplicationExtension

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

        extensions.configure<ApplicationExtension> {
            namespace  = "es.sebas1705.haltera"
            compileSdk = COMPILE_SDK
            defaultConfig {
                applicationId = "es.sebas1705.haltera"
                minSdk        = MIN_SDK
                targetSdk     = TARGET_SDK
                versionCode   = 1
                versionName   = "1.0.0"
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            buildTypes {
                debug   { isDebuggable = true }
                release {
                    isMinifyEnabled = true
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro",
                    )
                }
            }
        }
    }
}
