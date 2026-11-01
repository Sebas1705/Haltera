# Haltera: migrating a Kotlin Multiplatform app to AGP 9

How I moved a KMP gym logbook from Android Gradle Plugin 8.13 to 9.4 in two pull requests, and caught a packaging bug that every test had missed.

## Context

[Haltera](https://github.com/Sebas1705/Haltera) is a Kotlin Multiplatform app (Android and iOS, Compose Multiplatform UI) with a modular build: `core`, `data`, `domain` and `feature` modules, all configured through convention plugins in `build-logic`. Releases are built in CI from a composite action and verified by a script before they go to Firebase App Distribution and Google Play.

## Problem

The upgrade was forced by a dependency, not chosen. Dependabot opened a bump of coil from 3.5.0 to 3.6.3 ([#7](https://github.com/Sebas1705/Haltera/pull/7)). It merged green, but coil 3.6.3 pulls androidx.compose 1.12, which requires AGP 9.1, and the project was on AGP 8.13.2. The PR's checks did not include the Android job, so the breakage only showed on `main`, and I reverted it ([fb0953e](https://github.com/Sebas1705/Haltera/commit/fb0953e)). Behind it, the Compose group ([#45](https://github.com/Sebas1705/Haltera/pull/45)), ktor ([#6](https://github.com/Sebas1705/Haltera/pull/6)) and the Gradle wrapper ([#8](https://github.com/Sebas1705/Haltera/pull/8)) were also waiting. Staying on AGP 8 meant freezing dependencies.

AGP 9 is not a version bump for a KMP project. It no longer allows `com.android.library` next to `org.jetbrains.kotlin.multiplatform`, and it only accepts the application module's old shape (`com.android.application` plus the KMP `androidTarget()`) behind two opt-out flags that AGP 10 will remove.

## Approach

**Step 1: the upgrade itself ([#46](https://github.com/Sebas1705/Haltera/pull/46)).** AGP 8.13.2 → 9.4.1 and Gradle 9.5.1 → 9.8.1, in three commits, each verified on its own:

- Every library module moved to the Android Gradle library plugin for KMP (`com.android.kotlin.multiplatform.library`). Because all modules go through `haltera.kmp.library`, this was one change in `build-logic`: `kotlin { androidLibrary { … } }` instead of `androidTarget()` plus an `android {}` block. Libraries lose build types, so JVM tests moved from `src/androidUnitTest` to `src/androidHostTest` and run with `testAndroidHostTest`. Android resources became opt-in, so I enabled them only in modules with `src/androidMain/res`.
- The app module kept its old shape behind `android.builtInKotlin=false` and `android.newDsl=false`, documented as temporary. I deliberately left the release pipeline untouched in this PR.
- `compileSdk` went to 37 (required by coil 3.6.3 and androidx.compose 1.12), Detekt's test excludes were extended to `androidHostTest`, and CI, docs and the PR template now run both test tasks. The coil, ktor, Compose Multiplatform (1.12.2) and wrapper bumps were re-applied, superseding the three Dependabot PRs.

The suite passed with 1,161 tests and the debug APK built.

**Step 2: remove the opt-outs ([#47](https://github.com/Sebas1705/Haltera/pull/47)).** I split the Android application into a new pure-Android `androidApp` module (`com.android.application` with AGP's built-in Kotlin and the new DSL, through a new `haltera.android.app` convention). It took the manifest, launcher resources, activities, signing config, R8 rules, `google-services` and LeakCanary; `composeApp` became a regular KMP library holding the shared shell. CI, the release action, the release and distribution workflows and the verification scripts were repointed to the new output paths.

**The bug the tests didn't see.** After the split, the app crashed at startup: `composeApp`'s own Compose resource catalogue was missing from the APK. Compose Multiplatform ships a library's resources as Android assets, and the KMP library plugin only packages assets into the AAR when Android resources are enabled. Since #46 enabled them only for modules with `res/`, the strings of `core:ui` and five feature modules were already absent from the APK built from `main`; I confirmed it on that build's debug APK. The fix was one condition in the convention:

```kotlin
androidLibrary {
    this.namespace = namespace
    compileSdk = COMPILE_SDK
    minSdk = MIN_SDK
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    // The plugin only packages assets (where Compose Multiplatform puts a
    // library's resources) into the AAR when Android resources are enabled.
    androidResources {
        enable = file("src/androidMain/res").isDirectory ||
            file("src/commonMain/composeResources").isDirectory
    }
    withHostTest {}
}
```

## Result

- Both PRs merged on the same day; the project builds with AGP 9 defaults and no opt-out flags.
- After #47, `detekt testDebugUnitTest testAndroidHostTest verifySqlDelightMigration :androidApp:assembleDebug` ran green with 1,241 tests (160 of them in `composeApp`, now under `androidHostTest`).
- Debug APK on an Android 16 emulator: launch, routines, the routine catalogue, a full free session through to its summary, and data surviving a restart, in English and again in Spanish via per-app locale, with no errors in logcat.
- The release path was reproduced locally exactly as CI runs it: signed `assembleRelease bundleRelease`, `verify-release.mjs` (target/min SDK, 16 KB alignment, mapping file), `apksigner` showing the upload certificate, `aapt2` showing the package and version, and all 8 modules' Compose resources present.
- What I could not verify: the Play upload and App Distribution steps only run on `main` and tags, so only their input paths were checked.

## Lessons

- **A green test suite does not prove the artifact is right.** Host tests read resources from the module, not from the APK. The missing resources only showed up in the running app and in the package itself, so for me a build migration is not done until the APK has been inspected and run.
- **CI must gate what bots merge.** The coil breakage reached `main` because the Dependabot PR never ran the Android job.
- **Split risky migrations.** Keeping the release pipeline out of #46 made each PR reviewable and kept a working release path at every step.
- **Convention plugins pay off at migration time.** Changing the Android target for twenty-odd modules, and later fixing the resource packaging for all of them, were single edits in `build-logic`.
