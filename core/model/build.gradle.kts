plugins {
    id("submark.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // Annotations only: domain models double as Room entities to avoid a parallel entity layer.
    api(libs.room.common)
    api(libs.kotlinx.serialization.json)
}
