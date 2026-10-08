plugins {
    id("submark.android.application")
    id("submark.android.compose")
    id("submark.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.submark"

    defaultConfig {
        applicationId = "io.github.submark"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    androidResources {
        generateLocaleConfig = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:subscriptions"))
    implementation(project(":feature:money"))
    implementation(project(":feature:overview"))
    implementation(project(":feature:calendar"))
    implementation(project(":feature:analytics"))
    implementation(project(":feature:share"))
    implementation(project(":feature:notifications"))
    implementation(project(":feature:backup"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:integrations"))
    implementation(project(":feature:widget"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
}
