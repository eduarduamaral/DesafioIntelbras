import java.util.Properties

// SDKs, Java, lint, detekt, Compose e Hilt vêm dos convention plugins (build-logic).
plugins {
    alias(libs.plugins.desafio.android.application)
    alias(libs.plugins.desafio.android.compose)
    alias(libs.plugins.desafio.hilt)
}

// O token da API NÃO é versionado: vem do local.properties (ignorado pelo Git)
// ou da variável de ambiente API_TOKEN (útil no CI).
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(key: String, default: String = ""): String =
    localProperties.getProperty(key) ?: System.getenv(key) ?: default

android {
    namespace = "br.com.eduardoamaral.desafiointelbras"

    defaultConfig {
        applicationId = "br.com.eduardoamaral.desafiointelbras"
        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${secret("API_BASE_URL", "http://squadapps.ddns-intelbras.com.br:37025/")}\"",
        )
        buildConfigField("String", "API_TOKEN", "\"${secret("API_TOKEN")}\"")
    }
    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
}

dependencies {
    implementation(project(":core:domain"))
    // Só o :app conhece a camada de dados: liga o DataContainer ao Hilt e agenda o WorkManager.
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    // O app só monta a navegação: cada tela vive no seu módulo de feature.
    implementation(project(":feature:dashboard"))
    implementation(project(":feature:device"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)

    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    implementation(libs.work.runtime.ktx)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
