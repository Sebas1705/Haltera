plugins { alias(libs.plugins.kmp.data) }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":data:models"))
            implementation(project(":data:local"))
            implementation(project(":data:routines"))
            // HealthDataType and HealthSettingsStore appear in the health contracts.
            api(project(":core:datastore"))
            // In-memory fuzzy ranking of exercise and food searches.
            implementation(project(":core:search"))
            // Open Food Facts barcode lookups (nutrition).
            implementation(libs.ktor.client.core)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
        }
        androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
        iosMain.dependencies { implementation(libs.ktor.client.darwin) }
        androidHostTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.sqldelight.jvm.driver)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.ktor.client.mock)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.test)
        }
    }
}
