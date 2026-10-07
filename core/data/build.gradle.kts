plugins {
    id("submark.android.library")
    id("submark.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.submark.core.data"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:domain"))
    api(project(":core:database"))
    api(libs.datastore.preferences)
    api(libs.okhttp)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.android)
    implementation(libs.work.runtime.ktx)
    testImplementation(libs.okhttp.mockwebserver)
}
