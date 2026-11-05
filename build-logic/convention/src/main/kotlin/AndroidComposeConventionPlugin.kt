import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Liga o Compose no módulo Android e traz o BOM, o preview e as ferramentas de debug. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("kotlin-compose"))
        extensions.getByType(CommonExtension::class.java).buildFeatures.compose = true
        dependencies {
            val bom = platform(libs.library("androidx-compose-bom"))
            "implementation"(bom)
            "androidTestImplementation"(bom)
            "implementation"(libs.library("androidx-compose-ui-tooling-preview"))
            "debugImplementation"(libs.library("androidx-compose-ui-tooling"))
            "debugImplementation"(libs.library("androidx-compose-ui-test-manifest"))
        }
    }
}
