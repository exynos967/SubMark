plugins {
    id("submark.android.feature")
}

android {
    namespace = "io.github.submark.feature.settings"
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.biometric)
    implementation(libs.work.runtime.ktx)
}
