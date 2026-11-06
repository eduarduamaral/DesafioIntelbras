import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Módulo Kotlin Multiplatform sem Android (ex.: :core:domain).
 *
 * - jvm: é o que os módulos Android consomem (Java 11, como eles).
 * - iosArm64 (aparelho) e iosSimulatorArm64 (simulador em Mac Apple Silicon).
 *
 * Testes ficam em commonTest com kotlin.test e rodam em cada alvo: `jvmTest` em
 * qualquer máquina, `iosSimulatorArm64Test` só no Mac com Xcode, `allTests` roda todos.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("kotlin-multiplatform"))
        pluginManager.apply("desafio.detekt")
        extensions.configure<KotlinMultiplatformExtension> {
            jvm {
                compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
            }
            iosArm64()
            iosSimulatorArm64()

            sourceSets.getByName("commonTest").dependencies {
                implementation(libs.library("kotlin-test"))
            }
        }
        // A tarefa "detekt" olha src/main e src/test por padrão; num módulo KMP o código
        // fica em src/<sourceSet>/kotlin (commonMain, commonTest, iosMain...).
        extensions.configure<DetektExtension> {
            source.setFrom(fileTree("src") { include("**/*.kt") })
        }
    }
}
