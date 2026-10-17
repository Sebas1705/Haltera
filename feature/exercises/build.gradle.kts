plugins { alias(libs.plugins.kmp.feature) }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.bundles.compose.core)
            implementation(libs.navigation.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            // SavedStateHandle: the search and filter chips survive process death.
            implementation(libs.lifecycle.viewmodel.savedstate)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
            implementation(libs.bundles.koin.compose.bundle)
            // Exercise frames (spec §6.4), fetched from free-exercise-db and cached by Coil.
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)

            implementation(project(":core:common"))
            implementation(project(":core:ui"))
            implementation(project(":core:units"))
            implementation(project(":core:datastore"))
            // Repository interfaces, models and use cases (implementations stay internal to data:repository).
            implementation(project(":domain:usecases"))
        }
        // Ktor engines for Coil's network fetcher.
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}

compose.resources {
    packageOfResClass = "es.sebas1705.haltera.feature.exercises.resources"
    publicResClass = false
}
