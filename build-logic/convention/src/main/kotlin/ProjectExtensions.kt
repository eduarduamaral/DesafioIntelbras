import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

/** Catálogo de versões (gradle/libs.versions.toml) visto de dentro dos plugins. */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).get()

internal fun VersionCatalog.pluginId(alias: String): String =
    findPlugin(alias).get().get().pluginId

/** Versões de SDK do desafio: minSdk 29, compileSdk e targetSdk 36. */
internal object Sdk {
    const val MIN = 29
    const val COMPILE = 36
    const val TARGET = 36
}

/** O que todo módulo Android do projeto tem em comum (app e bibliotecas). */
internal fun configureAndroidCommon(android: CommonExtension) {
    android.compileSdk = Sdk.COMPILE
    android.defaultConfig.minSdk = Sdk.MIN
    android.defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    android.compileOptions.sourceCompatibility = JavaVersion.VERSION_11
    android.compileOptions.targetCompatibility = JavaVersion.VERSION_11
    android.lint.apply {
        // Erro de lint quebra o build (e o CI); avisos aparecem no relatório.
        abortOnError = true
        checkDependencies = false
        // Atualização de versões é decidida e testada à parte (ex.: Renovate/Dependabot),
        // não como aviso de lint a cada build.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }
}
