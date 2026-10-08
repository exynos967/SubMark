plugins {
    id("submark.android.feature")
}

android {
    namespace = "io.github.submark.feature.widget"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.glance)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
}
