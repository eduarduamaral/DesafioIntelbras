import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** detekt com as regras do ktlint e a configuração única em config/detekt/detekt.yml. */
class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("detekt"))
        extensions.configure<DetektExtension> {
            // Parte do padrão do detekt e só sobrescreve o que está no detekt.yml.
            buildUponDefaultConfig.set(true)
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
            parallel.set(true)
        }
        dependencies {
            "detektPlugins"(libs.library("detekt-rules-ktlint"))
        }
    }
}
