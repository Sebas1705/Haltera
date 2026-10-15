plugins { alias(libs.plugins.kmp.feature) }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.bundles.compose.core)
            implementation(libs.navigation.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
            implementation(libs.bundles.koin.compose.bundle)

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
        }
    }
}

compose.resources {
    packageOfResClass = "es.sebas1705.ironlog.feature.nutrition.resources"
    publicResClass = false
}
