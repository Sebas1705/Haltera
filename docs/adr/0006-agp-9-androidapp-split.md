# ADR 0006: AGP 9 and a pure-Android `androidApp` module

- **Status:** accepted
- **Date:** 2026-10-25 (#46 upgrade, #47 split)
- **Area:** `androidApp/`, `composeApp/`, `build-logic` (`haltera.kmp.library`, `haltera.android.app`), `gradle.properties`

## Context

AGP 9 does not allow `com.android.library` next to the Kotlin Multiplatform plugin. #46 moved every library module to
the Android Gradle library plugin for KMP (`com.android.kotlin.multiplatform.library`). `composeApp` stayed an
application with `androidTarget()`, which AGP 9 only accepts with `android.builtInKotlin=false` and
`android.newDsl=false`. Those opt-outs were documented as temporary because AGP 10 removes them.

## Decision

- Library modules use `haltera.kmp.library`: `androidLibrary {}`, no build types, JVM tests in `src/androidHostTest`
  run with `testAndroidHostTest`. Android resources are enabled only for modules with `src/androidMain/res` or
  `src/commonMain/composeResources`.
- `androidApp` is the only pure-Android module (`haltera.android.app`: `com.android.application` with the built-in
  Kotlin of AGP and the new DSL, no KMP plugin). It holds the manifest, launcher resources, signing,
  `google-services.json`, R8 rules and the Android entry points.
- `composeApp` becomes a KMP library (`haltera.kmp.feature`) that `androidApp` and `iosApp` depend on.
- The two opt-outs were removed from `gradle.properties`.

## Consequences

- CI, the release pipeline and scripts build `:androidApp:assembleRelease`/`bundleRelease` and read outputs from
  `androidApp/build/outputs`.
- The first split missed the Compose Multiplatform resources of library modules (they ship as assets, which the
  KMP library plugin only packs when Android resources are enabled); the convention now enables them for every
  module that has `composeResources`.
- Host tests use the `androidHostTest` source set, which Detekt's default test excludes do not know, so the rules
  that skip tests list it explicitly.
