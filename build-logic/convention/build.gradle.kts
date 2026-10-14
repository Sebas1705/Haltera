plugins {
    `kotlin-dsl`
}

group = "es.sebas1705.ironlog.buildlogic"

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
    compileOnly(libs.plugins.sqldelight.toDep())
    compileOnly(libs.plugins.detekt.toDep())
}

fun Provider<PluginDependency>.toDep() = map {
    "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}"
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "ironlog.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpFeature") {
            id = "ironlog.kmp.feature"
            implementationClass = "KmpFeatureConventionPlugin"
        }
        register("kmpData") {
            id = "ironlog.kmp.data"
            implementationClass = "KmpDataConventionPlugin"
        }
        register("kmpDomain") {
            id = "ironlog.kmp.domain"
            implementationClass = "KmpDomainConventionPlugin"
        }
        register("kmpCore") {
            id = "ironlog.kmp.core"
            implementationClass = "KmpCoreConventionPlugin"
        }
        register("kmpApp") {
            id = "ironlog.kmp.app"
            implementationClass = "KmpAppConventionPlugin"
        }
        register("ironlogDetekt") {
            id = "ironlog.detekt"
            implementationClass = "DetektConventionPlugin"
        }
    }
}
