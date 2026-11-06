import java.util.Properties

// Implementação do domínio: Room, Retrofit, fila de pendências e WorkManager.
// As features não dependem deste módulo; só o :app, que monta o grafo do Hilt.
plugins {
    alias(libs.plugins.desafio.android.library)
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
    namespace = "br.com.eduardoamaral.desafiointelbras.core.data"

    defaultConfig {
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
}

room {
    // Exporta o schema para versionar as migrações do banco
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":core:domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    testImplementation(project(":core:testing"))
    testImplementation(libs.work.testing)

    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
