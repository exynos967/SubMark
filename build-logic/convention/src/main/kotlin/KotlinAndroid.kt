import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal object SubMarkConfig {
    const val COMPILE_SDK = 35
    const val MIN_SDK = 29
    const val TARGET_SDK = 35
}

internal fun Project.configureKotlinAndroid(extension: CommonExtension<*, *, *, *, *, *>) {
    extension.apply {
        compileSdk = SubMarkConfig.COMPILE_SDK
        defaultConfig.minSdk = SubMarkConfig.MIN_SDK
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
    configureKotlin()
}

internal fun Project.configureKotlin() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
                "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            )
        }
    }
}

internal fun org.gradle.api.artifacts.dsl.DependencyHandler.implementation(dep: Any) = add("implementation", dep)
internal fun org.gradle.api.artifacts.dsl.DependencyHandler.ksp(dep: Any) = add("ksp", dep)
internal fun org.gradle.api.artifacts.dsl.DependencyHandler.testImplementation(dep: Any) = add("testImplementation", dep)
internal fun org.gradle.api.artifacts.dsl.DependencyHandler.debugImplementation(dep: Any) = add("debugImplementation", dep)
