
plugins {
    alias(libs.plugins.kmp.app)
}


// ── Environment profiles (ADR-0018) ──────────────────────────────────────────
kotlin {
    sourceSets {

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.animation)
            implementation(compose.components.resources)
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.navigation.compose)
            implementation(libs.kotlinx.serialization.json)

            // Koin
            implementation(libs.koin.core)
            implementation(libs.bundles.koin.compose.bundle)

            // Core
            implementation(project(":core:common"))
            implementation(project(":core:ui"))
            implementation(project(":core:security"))
            implementation(project(":core:network"))
            implementation(project(":core:datastore"))
            implementation(project(":core:logging"))

            // Data
            implementation(project(":data:models"))
            implementation(project(":data:local"))
            implementation(project(":data:network"))
            implementation(project(":data:repository"))

            // Domain
            implementation(project(":domain:usecases"))

            // Features
            implementation(project(":feature:auth"))
            implementation(project(":feature:home"))
            implementation(project(":feature:settings"))
            implementation(project(":feature:profile"))
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
        }

    }
}

dependencies {
    debugImplementation(libs.leakcanary)
}

