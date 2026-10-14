rootProject.name = "Ironlog"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google { mavenContent { includeGroupAndSubgroups("androidx"); includeGroupAndSubgroups("com.android"); includeGroupAndSubgroups("com.google") } }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google { mavenContent { includeGroupAndSubgroups("androidx"); includeGroupAndSubgroups("com.android"); includeGroupAndSubgroups("com.google") } }
        mavenCentral()
    }
}

// ── App entry-points ─────────────────────────────────────────────────────────
include(":composeApp")

// ── Core ─────────────────────────────────────────────────────────────────────
include(
    ":core:common",
    ":core:ui",
    ":core:security",
    ":core:network",
    ":core:datastore",
    ":core:logging",
    ":core:testing",
)

// ── Data ─────────────────────────────────────────────────────────────────────
include(
    ":data:models",
    ":data:local",
    ":data:network",
    ":data:repository",
)

// ── Domain ────────────────────────────────────────────────────────────────────
include(":domain:usecases")

// ── Features ──────────────────────────────────────────────────────────────────
include(
    ":feature:auth",
    ":feature:home",
    ":feature:settings",
    ":feature:profile",
)
