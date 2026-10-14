plugins {
    alias(libs.plugins.kotlin.multiplatform)  apply false
    alias(libs.plugins.kotlin.serialization)  apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler)      apply false
    alias(libs.plugins.android.application)   apply false
    alias(libs.plugins.android.library)       apply false
    alias(libs.plugins.sqldelight)            apply false
    alias(libs.plugins.detekt)
}

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
    autoCorrect = providers.environmentVariable("CI").orNull == null
}

dependencies {
    detektPlugins(libs.detekt.formatting)
}

// Detekt 1.23.x is compiled against Kotlin 2.0.21; Gradle would otherwise align its
// Kotlin dependencies with the project's Kotlin and Detekt fails at startup.
val detektKotlinVersion: String = libs.versions.detekt.kotlin.get()
allprojects {
    configurations.matching { it.name == "detekt" || it.name == "detektPlugins" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") useVersion(detektKotlinVersion)
        }
    }
}
