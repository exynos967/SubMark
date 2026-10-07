plugins {
    id("submark.android.library")
    id("submark.hilt")
    alias(libs.plugins.room)
}

android {
    namespace = "io.github.submark.core.database"
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":core:model"))
    api(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.room.testing)
}
