package ext

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal fun Project.configureKmp(
    block: KotlinMultiplatformExtension.() -> Unit = {},
) {
    extensions.configure<KotlinMultiplatformExtension> {
        // ── Android ──────────────────────────────────────────────────────────
        androidTarget {
            compilations.all {
                kotlinOptions { jvmTarget = "17" }
            }
        }
        // ── Desktop (JVM) ────────────────────────────────────────────────────
        // ── iOS ──────────────────────────────────────────────────────────────
        listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = project.name
                isStatic = true
            }
        }
        // ── Common compiler options ───────────────────────────────────────────
        targets.configureEach {
            compilations.configureEach {
                compileTaskProvider.configure {
                    compilerOptions {
                        freeCompilerArgs.add("-Xexpect-actual-classes")
                    }
                }
            }
        }
        block()
    }
    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions { jvmTarget = "17" }
    }
}

internal fun Project.configureAndroidLibrary(namespace: String) {
    extensions.configure<CommonExtension<*, *, *, *, *, *>>("android") {
        this.namespace = namespace
        compileSdk = 35
        defaultConfig { minSdk = 24 }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}
