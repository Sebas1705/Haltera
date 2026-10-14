plugins { alias(libs.plugins.kmp.core) }

kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
