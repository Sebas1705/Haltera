plugins { alias(libs.plugins.kmp.data) }

// The phone's health platform behind data:repository's HealthGateway: Health Connect on Android.
// iOS has no implementation yet (HealthKit is a follow-up); the app binds UnavailableHealthGateway there.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":data:repository"))
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.health.connect.client)
        }
        androidHostTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
