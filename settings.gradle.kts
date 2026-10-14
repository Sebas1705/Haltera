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

include(":composeApp")

include(
    ":core:common",
    ":core:ui",
    ":core:logging",
    ":core:testing",
    ":core:units",
    ":core:datastore",
)

include(":data:models", ":data:local", ":data:repository", ":data:routines")

include(":domain:usecases")
