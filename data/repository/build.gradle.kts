plugins { alias(libs.plugins.kmp.data) }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":data:models"))
            implementation(project(":data:local"))
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidUnitTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.sqldelight.jvm.driver)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
