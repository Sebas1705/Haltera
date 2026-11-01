plugins {
    `kotlin-dsl`
}

group = "es.sebas1705.haltera.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(libs.plugins.kotlin.multiplatform.toDep())
    compileOnly(libs.plugins.kotlin.serialization.toDep())
    compileOnly(libs.plugins.compose.multiplatform.toDep())
    compileOnly(libs.plugins.compose.compiler.toDep())
    compileOnly(libs.plugins.android.application.toDep())
    compileOnly(libs.plugins.android.library.toDep())
    compileOnly(libs.plugins.android.kmp.library.toDep())
    compileOnly(libs.plugins.sqldelight.toDep())
    compileOnly(libs.plugins.detekt.toDep())
    compileOnly(libs.plugins.kover.toDep())
}

fun Provider<PluginDependency>.toDep() = map {
    "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}"
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "haltera.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpFeature") {
            id = "haltera.kmp.feature"
            implementationClass = "KmpFeatureConventionPlugin"
        }
        register("kmpData") {
            id = "haltera.kmp.data"
            implementationClass = "KmpDataConventionPlugin"
        }
        register("kmpDomain") {
            id = "haltera.kmp.domain"
            implementationClass = "KmpDomainConventionPlugin"
        }
        register("kmpCore") {
            id = "haltera.kmp.core"
            implementationClass = "KmpCoreConventionPlugin"
        }
        register("androidApp") {
            id = "haltera.android.app"
            implementationClass = "AndroidAppConventionPlugin"
        }
        register("halteraDetekt") {
            id = "haltera.detekt"
            implementationClass = "DetektConventionPlugin"
        }
    }
}
