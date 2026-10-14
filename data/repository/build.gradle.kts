plugins { alias(libs.plugins.kmp.data) }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":data:models"))
            implementation(project(":data:local"))
            implementation(project(":data:routines"))
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
        }
        androidUnitTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.sqldelight.jvm.driver)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
