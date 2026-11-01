plugins {
    alias(libs.plugins.kotlin.multiplatform)  apply false
    alias(libs.plugins.kotlin.serialization)  apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler)      apply false
    alias(libs.plugins.android.application)   apply false
    alias(libs.plugins.android.library)       apply false
    alias(libs.plugins.android.kmp.library)   apply false
    alias(libs.plugins.sqldelight)            apply false
    alias(libs.plugins.google.services)       apply false
    alias(libs.plugins.firebase.crashlytics)  apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

// Code coverage (Kotlinx Kover). Every KMP module applies Kover through KmpLibraryConventionPlugin and the root
// merges their reports. The modules' JVM tests are the Android host tests (testAndroidHostTest), which is what
// Kover's default "total" variant measures:
//   ./gradlew koverXmlReport koverHtmlReport koverVerify
// Left out: :core:testing (test helpers) and :androidApp (Android entry points, no unit tests).
dependencies {
    kover(projects.composeApp)
    kover(projects.core.common)
    kover(projects.core.ui)
    kover(projects.core.logging)
    kover(projects.core.units)
    kover(projects.core.datastore)
    kover(projects.core.search)
    kover(projects.data.models)
    kover(projects.data.local)
    kover(projects.data.repository)
    kover(projects.data.routines)
    kover(projects.data.sync)
    kover(projects.data.remote)
    kover(projects.data.health)
    kover(projects.domain.usecases)
    kover(projects.feature.today)
    kover(projects.feature.routines)
    kover(projects.feature.exercises)
    kover(projects.feature.progress)
    kover(projects.feature.session)
    kover(projects.feature.nutrition)
}

kover {
    reports {
        filters {
            excludes {
                // SQLDelight generated database, queries and adapters.
                packages("es.sebas1705.haltera.data.local.db")
                // Compose Multiplatform generated resource accessors (Res, String0_commonMainKt, ...).
                classes("es.sebas1705.haltera.resources.*", "es.sebas1705.haltera.*.resources.*")
                // Android generated classes and compiler synthetics (Compose singletons, serializers).
                classes("*.BuildConfig", "*ComposableSingletons*", "*\$\$serializer")
                // Koin DI modules and Compose previews.
                classes("*.di.*", "*Module", "*ModuleKt")
                annotatedBy(
                    "androidx.compose.ui.tooling.preview.Preview",
                    "org.jetbrains.compose.ui.tooling.preview.Preview",
                )
            }
        }
        verify {
            rule("Minimum line coverage") {
                // Current coverage rounded down to the nearest 5 %; raise it when coverage grows, never lower it.
                minBound(45)
            }
        }
    }
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
