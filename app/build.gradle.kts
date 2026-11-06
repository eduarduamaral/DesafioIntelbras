// SDKs, Java, lint, detekt, Compose e Hilt vêm dos convention plugins (build-logic).
plugins {
    alias(libs.plugins.desafio.android.application)
    alias(libs.plugins.desafio.android.compose)
    alias(libs.plugins.desafio.hilt)
    alias(libs.plugins.kotlin.serialization)
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

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)

    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.hilt.work)

    implementation(libs.work.runtime.ktx)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(project(":core:testing"))

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
