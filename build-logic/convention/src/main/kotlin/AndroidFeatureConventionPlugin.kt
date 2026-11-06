import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Módulo de feature: biblioteca Android com Compose e Hilt, mais o que toda tela usa
 * (ViewModel, Navigation type-safe e os módulos core). Uma feature nunca depende de
 * :core:data nem de outra feature; quem liga as features é o :app.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("desafio.android.library")
        pluginManager.apply("desafio.android.compose")
        pluginManager.apply("desafio.hilt")
        // Rotas @Serializable do Navigation.
        pluginManager.apply(libs.pluginId("kotlin-serialization"))
        dependencies {
            "implementation"(project(":core:ui"))
            "implementation"(project(":core:domain"))

            "implementation"(libs.library("androidx-lifecycle-runtime-compose"))
            "implementation"(libs.library("androidx-lifecycle-viewmodel-compose"))
            "implementation"(libs.library("androidx-navigation-compose"))
            "implementation"(libs.library("hilt-lifecycle-viewmodel-compose"))
            "implementation"(libs.library("kotlinx-serialization-json"))

            "testImplementation"(project(":core:testing"))

            "androidTestImplementation"(libs.library("androidx-compose-ui-test-junit4"))
            "androidTestImplementation"(libs.library("kotlinx-coroutines-test"))
        }
    }
}
