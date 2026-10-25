package ext

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

internal const val COMPILE_SDK = 37
internal const val TARGET_SDK = 36
internal const val MIN_SDK = 24

/**
 * The targets shared by every module: iOS frameworks and the common compiler flags.
 * The Android target of every KMP module is configured by [configureAndroidLibrary]
 * (`com.android.kotlin.multiplatform.library`, `androidLibrary {}`); the application module is pure Android
 * ([configureAndroidApp]) and depends on composeApp.
 */
internal fun Project.configureKmp(
    block: KotlinMultiplatformExtension.() -> Unit = {},
) {
    extensions.configure<KotlinMultiplatformExtension> {
        listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = project.name
                isStatic = true
            }
        }
        compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
        block()
    }
}

/**
 * androidApp: `com.android.application` with built-in Kotlin (AGP 9), so no Kotlin plugin is applied and the
 * Kotlin jvmTarget follows `compileOptions.targetCompatibility`.
 */
internal fun Project.configureAndroidApp(namespace: String, applicationId: String) {
    extensions.configure<ApplicationExtension> {
        this.namespace = namespace
        compileSdk = COMPILE_SDK
        defaultConfig {
            this.applicationId = applicationId
            minSdk = MIN_SDK
            targetSdk = TARGET_SDK
            versionCode = 1
            versionName = "1.0.0"
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        buildTypes {
            debug { isDebuggable = true }
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

/**
 * Library modules: the Android Gradle library plugin for KMP (`com.android.kotlin.multiplatform.library`).
 * It has no build types; JVM tests live in `src/androidHostTest` and run with `testAndroidHostTest`.
 */
internal fun Project.configureAndroidLibrary(namespace: String) {
    extensions.configure<KotlinMultiplatformExtension> {
        (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("androidLibrary") {
            this.namespace = namespace
            compileSdk = COMPILE_SDK
            minSdk = MIN_SDK
            compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
            // Off by default in this plugin. Modules with src/androidMain/res (notification strings, icons) need it,
            // and so do modules with Compose resources: the plugin only packages assets (where Compose Multiplatform
            // puts a library's resources) into the AAR when Android resources are enabled.
            androidResources {
                enable = file("src/androidMain/res").isDirectory || file("src/commonMain/composeResources").isDirectory
            }
            withHostTest {}
        }
    }
}
