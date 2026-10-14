plugins {
    alias(libs.plugins.kmp.app)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.bundles.compose.core)
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kermit)

            implementation(libs.koin.core)
            implementation(libs.bundles.koin.compose.bundle)

            implementation(project(":core:common"))
            implementation(project(":core:ui"))
            implementation(project(":core:logging"))
            implementation(project(":core:units"))
            implementation(project(":core:datastore"))
            implementation(project(":data:local"))
            implementation(project(":data:repository"))
            implementation(project(":domain:usecases"))

            implementation(project(":feature:today"))
            implementation(project(":feature:routines"))
            implementation(project(":feature:exercises"))
            implementation(project(":feature:progress"))
            implementation(project(":feature:session"))
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
        }
    }
}

android {
    buildTypes {
        getByName("debug") { applicationIdSuffix = ".debug" }
    }
}

dependencies {
    debugImplementation(libs.leakcanary)
}

compose.resources {
    packageOfResClass = "es.sebas1705.ironlog.resources"
    publicResClass = false
}

// The Swift app imports the shared code as `ComposeApp` (iosApp/iosApp/*.swift).
kotlin {
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.withType<org.jetbrains.kotlin.gradle.plugin.mpp.Framework>().configureEach {
            baseName = "ComposeApp"
        }
    }
}
