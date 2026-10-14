plugins { alias(libs.plugins.kmp.core) }

kotlin {
    sourceSets {
        commonMain.dependencies { implementation(libs.kermit) }
    }
}
