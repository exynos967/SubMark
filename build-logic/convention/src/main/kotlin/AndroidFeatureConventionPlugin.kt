import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Feature module = Android library + Compose + Hilt + the core modules every screen needs. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("submark.android.library")
        pluginManager.apply("submark.android.compose")
        pluginManager.apply("submark.hilt")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:domain"))
            implementation(project(":core:data"))
            implementation(project(":core:ui"))
            implementation(libs.findLibrary("androidx-lifecycle-runtime-compose").get())
            implementation(libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            implementation(libs.findLibrary("androidx-navigation-compose").get())
            implementation(libs.findLibrary("hilt-navigation-compose").get())
            implementation(libs.findLibrary("kotlinx-serialization-json").get())
            implementation(libs.findLibrary("kotlinx-coroutines-android").get())
        }
    }
}
