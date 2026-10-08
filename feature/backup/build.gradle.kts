plugins {
    id("submark.android.feature")
}

android {
    namespace = "io.github.submark.feature.backup"
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
}
