plugins { alias(libs.plugins.kmp.feature) }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.bundles.compose.core)
            implementation(libs.navigation.compose)
            // BackHandler: log mode asks before leaving a session with nothing logged.
            implementation(libs.compose.ui.backhandler)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
            implementation(libs.bundles.koin.compose.bundle)
            // Persists the rest timer (absolute end times) so it survives process death.
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)

            implementation(project(":core:common"))
            implementation(project(":core:ui"))
            implementation(project(":core:units"))
            implementation(project(":core:datastore"))
            // Repository interfaces, models and use cases (implementations stay internal to data:repository).
            implementation(project(":domain:usecases"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(project(":core:testing"))
        }
    }
}

compose.resources {
    packageOfResClass = "es.sebas1705.haltera.feature.session.resources"
    publicResClass = false
}
