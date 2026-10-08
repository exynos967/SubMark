import java.util.Properties

plugins {
    id("submark.android.application")
    id("submark.android.compose")
    id("submark.hilt")
    alias(libs.plugins.kotlin.serialization)
}

// Release signing: CI passes SUBMARK_* env vars; locally an (ignored) keystore.properties works too.
// With neither, release builds fall back to the debug key so they still install for local testing.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun signingValue(key: String, env: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(key)

android {
    namespace = "io.github.submark"

    defaultConfig {
        applicationId = "io.github.submark"
        // The release workflow overrides these from the tag and run number.
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "0.1.0"
    }

    signingConfigs {
        val storePath = signingValue("storeFile", "SUBMARK_KEYSTORE_FILE")
        if (storePath != null) {
            create("release") {
                storeFile = rootProject.file(storePath)
                storePassword = signingValue("storePassword", "SUBMARK_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "SUBMARK_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "SUBMARK_KEY_PASSWORD")
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("x86_64", "armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
