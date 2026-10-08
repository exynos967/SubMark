plugins {
    id("submark.android.feature")
}

android {
    namespace = "io.github.submark.feature.overview"
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
