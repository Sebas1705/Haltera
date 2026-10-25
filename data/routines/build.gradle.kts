plugins { alias(libs.plugins.kmp.data) }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":data:models"))
        }
        androidHostTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
