import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Hilt com KSP: o código de injeção é gerado na compilação. */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("ksp"))
        pluginManager.apply(libs.pluginId("hilt"))
        dependencies {
            "implementation"(libs.library("hilt-android"))
            "ksp"(libs.library("hilt-compiler"))
        }
    }
}
