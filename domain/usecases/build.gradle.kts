plugins { alias(libs.plugins.kmp.domain) }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:common"))
            api(project(":data:models"))
            api(project(":data:repository"))
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        // Scores every famous routine against the bundled catalogue (RoutineScorerFamousTest).
        androidHostTest.dependencies {
            implementation(project(":data:routines"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
