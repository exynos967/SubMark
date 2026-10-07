plugins {
    id("submark.android.library")
    id("submark.android.compose")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.submark.core.ui"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:domain"))
    api(libs.coil.compose)
    api(libs.androidx.navigation.compose)
    api(libs.reorderable)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
}
