plugins { alias(libs.plugins.kmp.core) }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.datastore.preferences.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.datastore.preferences)
            implementation(project(":core:security"))
        }
    }
}
