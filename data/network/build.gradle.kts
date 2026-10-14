plugins { alias(libs.plugins.kmp.data) }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:network"))
            implementation(project(":data:models"))
            implementation(libs.bundles.ktor.common)
            implementation(libs.koin.core)
        }
    }
}
