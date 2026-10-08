plugins {
    id("submark.android.feature")
}

android {
    namespace = "io.github.submark.feature.money"
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
