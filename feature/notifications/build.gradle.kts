plugins {
    id("submark.android.feature")
}

android {
    namespace = "io.github.submark.feature.notifications"
    defaultConfig {
        multiDexEnabled = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
