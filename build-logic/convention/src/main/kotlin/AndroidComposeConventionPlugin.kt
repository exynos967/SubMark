import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        val extension = extensions.getByName("android") as CommonExtension<*, *, *, *, *, *>
        extension.buildFeatures.compose = true
        dependencies {
            val bom = platform(libs.findLibrary("androidx-compose-bom").get())
            implementation(bom)
            implementation(libs.findLibrary("androidx-compose-ui").get())
            implementation(libs.findLibrary("androidx-compose-ui-graphics").get())
            implementation(libs.findLibrary("androidx-compose-foundation").get())
            implementation(libs.findLibrary("androidx-compose-material3").get())
            implementation(libs.findLibrary("androidx-compose-material-icons-extended").get())
            implementation(libs.findLibrary("androidx-compose-ui-tooling-preview").get())
            debugImplementation(libs.findLibrary("androidx-compose-ui-tooling").get())
        }
    }
}
