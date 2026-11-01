plugins {
    alias(libs.plugins.android.app)
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
// git-ignored. Without it the app still builds (CI checks, fresh clones) and runs with sync and crash reports off.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    // Injects the build id Crashlytics needs at startup and uploads the R8 mapping of release builds, so crash
    // traces are readable in the Firebase console.
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
} else {
    logger.warn("androidApp/google-services.json missing: building without Firebase config")
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
        getByName("debug") {
            applicationIdSuffix = ".debug"
            // Crash reports only from the builds people use; local crashes stay local.
            manifestPlaceholders["crashlyticsEnabled"] = "false"
        }
        getByName("release") {
            // On unless the user turned "Send crash reports" off in Settings (HalteraApplication applies the switch).
            manifestPlaceholders["crashlyticsEnabled"] = "true"
            signingConfig = signingConfigs.getByName(if (ciKeystorePath != null) "release" else "debug")
        }
    }
}

dependencies {
    // The shared app (UI, DI, platform bridges) and what the Android entry points wire into Koin.
    implementation(project(":composeApp"))
    implementation(project(":data:local"))
    implementation(project(":data:remote"))
    implementation(project(":data:repository"))
    implementation(project(":data:health"))
    implementation(project(":core:datastore"))
    implementation(project(":feature:routines"))
    implementation(project(":feature:nutrition"))

    implementation(libs.bundles.compose.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.gitlive.firebase.crashlytics)

    debugImplementation(libs.leakcanary)
}
