plugins {
    alias(libs.plugins.kmp.app)
}

// Release builds in CI (.github/actions/build-release) get, as environment variables:
//  - VERSION_CODE: the commit count of main, so every build installs over the previous one.
//  - VERSION_NAME: the build name (main-<n> for builds of main).
//  - SIGNING_*: the release (upload) keystore, decoded from Doppler ironlog/prd.
// Locally they are absent: the convention plugin's versionCode/versionName and a release build
// signed with the debug key.
val ciVersionCode: Int? = System.getenv("VERSION_CODE")?.toIntOrNull()
val ciVersionName: String? = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() }
val ciKeystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }

// The Firebase config comes from Doppler (scripts/doppler-sync-local.sh, or build-release in CI) and is
// git-ignored. Without it the app still builds (CI checks, fresh clones) and runs with sync off.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
} else {
    logger.warn("composeApp/google-services.json missing: building without Firebase config")
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
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play)
            implementation(libs.google.identity.googleid)
            // QR scanning for routine import, without the camera permission (Google code scanner UI).
            implementation(libs.play.services.code.scanner)
        }
    }
}

android {
    defaultConfig {
        ciVersionCode?.let { versionCode = it }
        ciVersionName?.let { versionName = it }
    }
    signingConfigs {
        if (ciKeystorePath != null) {
            create("release") {
                storeFile = file(ciKeystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("debug") { applicationIdSuffix = ".debug" }
        getByName("release") {
            signingConfig = signingConfigs.getByName(if (ciKeystorePath != null) "release" else "debug")
        }
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
