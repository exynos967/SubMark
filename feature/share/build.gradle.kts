plugins {
    id("submark.android.feature")
}

android {
    namespace = "io.github.submark.feature.share"
}

dependencies {
    implementation(libs.zxing.core)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
