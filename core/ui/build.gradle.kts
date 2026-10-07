plugins {
    id("submark.android.library")
    id("submark.android.compose")
}

android {
    namespace = "io.github.submark.core.ui"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:domain"))
    api(libs.coil.compose)
    implementation(libs.androidx.core.ktx)
}
