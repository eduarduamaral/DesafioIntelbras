import java.util.Properties

// API falsa do desafio para desenvolvimento local (o servidor original saiu do ar).
// Roda só no computador: nenhum módulo do app depende deste, então nada disto vai para o APK.
//   ./gradlew :tools:mock-api:run
plugins {
    alias(libs.plugins.desafio.jvm.library)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("br.com.eduardoamaral.desafiointelbras.mockapi.MainKt")
}

// Usa o mesmo API_TOKEN do local.properties que o app envia, para o 403 funcionar como no servidor real.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

tasks.named<JavaExec>("run") {
    environment("API_TOKEN", localProperties.getProperty("API_TOKEN") ?: System.getenv("API_TOKEN").orEmpty())
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(libs.ktor.server.test.host)
}
