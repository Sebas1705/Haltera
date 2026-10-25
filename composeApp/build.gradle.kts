plugins {
    alias(libs.plugins.kmp.feature)
}

// composeApp is the KMP library with the shared app: root DI, navigation shell, Settings and the platform
// bridges. The Android application (manifest, launcher resources, signing, entry points) is androidApp;
// the iOS one is iosApp, which imports this module as the `ComposeApp` framework.
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
            implementation(libs.kotlinx.datetime)
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
            implementation(project(":data:sync"))
            implementation(project(":data:remote"))
            implementation(project(":domain:usecases"))

            implementation(project(":feature:today"))
            implementation(project(":feature:routines"))
            implementation(project(":feature:exercises"))
            implementation(project(":feature:progress"))
            implementation(project(":feature:session"))
            implementation(project(":feature:nutrition"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.test)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play)
            implementation(libs.google.identity.googleid)
            // QR scanning for routine import, without the camera permission (Google code scanner UI).
            implementation(libs.play.services.code.scanner)
            // Health Connect gateway (data:health) and its permission contract.
            implementation(project(":data:health"))
            implementation(libs.androidx.health.connect.client)
        }
    }
}

compose.resources {
    packageOfResClass = "es.sebas1705.haltera.resources"
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
