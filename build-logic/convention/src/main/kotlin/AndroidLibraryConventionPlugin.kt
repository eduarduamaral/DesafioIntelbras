import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Biblioteca Android (módulos core e feature): mesma base do app, com detekt e JUnit. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("android-library"))
        pluginManager.apply("desafio.detekt")
        extensions.configure<LibraryExtension> {
            configureAndroidCommon(this)
        }
        dependencies {
            "testImplementation"(libs.library("junit"))
            "androidTestImplementation"(libs.library("androidx-junit"))
            "androidTestImplementation"(libs.library("androidx-espresso-core"))
        }
    }
}
