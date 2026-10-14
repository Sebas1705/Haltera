plugins { alias(libs.plugins.kmp.feature) }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(project(":core:common"))
            implementation(project(":core:ui"))
            implementation(project(":data:models"))
            implementation(project(":domain:usecases"))
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.bundles.koin.compose.bundle)
            implementation(libs.kotlinx.collections.immutable)
        }
    }
}
