plugins { alias(libs.plugins.kmp.core) }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.bundles.settings.bundle)
        }
        androidMain.dependencies {
            implementation(libs.security.crypto)
            implementation(libs.multiplatform.settings.no.arg)
        }
        iosMain.dependencies {
            implementation(libs.multiplatform.settings.no.arg)
        }
    }
}
