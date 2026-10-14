import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform)    apply false
    alias(libs.plugins.kotlin.serialization)    apply false
    alias(libs.plugins.compose.multiplatform)   apply false
    alias(libs.plugins.compose.compiler)        apply false
    alias(libs.plugins.android.application)     apply false
    alias(libs.plugins.android.library)         apply false
    alias(libs.plugins.ksp)                     apply false
    alias(libs.plugins.sqldelight)              apply false
    alias(libs.plugins.buildkonfig)             apply false
    alias(libs.plugins.detekt)
}

// ── Root Detekt ───────────────────────────────────────────────────────────────
detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
    autoCorrect = true
}

dependencies {
    detektPlugins(libs.detekt.formatting)
}

// ── Aggregation tasks ─────────────────────────────────────────────────────────
tasks.register("coverageUnitTestAll") {
    group = "verification"
    description = "Run all unit tests and aggregate coverage reports"
    subprojects.forEach { sub ->
        sub.tasks.matching { it.name == "jvmTest" || it.name == "testDebugUnitTest" }
            .forEach { dependsOn(it) }
    }
}

tasks.register("lintAll") {
    group = "verification"
    description = "Run lint on all Android modules"
    subprojects.forEach { sub ->
        sub.tasks.matching { it.name == "lintDebug" }.forEach { dependsOn(it) }
    }
}
