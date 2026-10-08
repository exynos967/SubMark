plugins {
    id("submark.android.feature")
}

android {
    namespace = "io.github.submark.feature.integrations"
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    implementation(libs.androidx.activity.compose)
    ksp(libs.hilt.work.compiler)
}
