plugins {
    alias(libs.plugins.kmp.data)
    alias(libs.plugins.sqldelight)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(project(":data:models"))
        }
        androidMain.dependencies  { implementation(libs.sqldelight.android.driver) }
        iosMain.dependencies    { implementation(libs.sqldelight.native.driver) }
    }
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("es.sebas1705.ironlog.data.local.db")
            srcDirs.setFrom("src/commonMain/sqldelight")
        }
    }
}
