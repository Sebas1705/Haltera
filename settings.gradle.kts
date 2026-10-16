rootProject.name = "Haltera"

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
    ":core:search",
)

include(":data:models", ":data:local", ":data:repository", ":data:routines", ":data:sync", ":data:remote", ":data:health")

include(":domain:usecases")

include(
    ":feature:today",
    ":feature:routines",
    ":feature:exercises",
    ":feature:progress",
    ":feature:session",
    ":feature:nutrition",
)
