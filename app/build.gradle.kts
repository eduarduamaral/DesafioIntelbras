// SDKs, Java, lint, detekt, Compose e Hilt vêm dos convention plugins (build-logic).
plugins {
    alias(libs.plugins.desafio.android.application)
    alias(libs.plugins.desafio.android.compose)
    alias(libs.plugins.desafio.hilt)
}

android {
    namespace = "br.com.eduardoamaral.desafiointelbras"

    defaultConfig {
        applicationId = "br.com.eduardoamaral.desafiointelbras"
        versionCode = 1
        versionName = "1.0"
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
    // Só o :app conhece a camada de dados: ele monta o grafo do Hilt e inicia o WorkManager.
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

    implementation(libs.work.runtime.ktx)

    implementation(libs.kotlinx.coroutines.android)
}
