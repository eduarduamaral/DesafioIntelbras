import java.util.Properties

// SDKs, Java, lint, detekt, Compose e Hilt vêm dos convention plugins (build-logic).
plugins {
    alias(libs.plugins.desafio.android.application)
    alias(libs.plugins.desafio.android.compose)
    alias(libs.plugins.desafio.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room)
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

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    buildFeatures {
        buildConfig = true
    }
}

room {
    // Exporta o schema para versionar as migrações do banco
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core:domain"))

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
    ksp(libs.hilt.androidx.compiler)

    implementation(libs.work.runtime.ktx)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.work.testing)

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
