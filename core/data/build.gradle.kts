// Implementação do domínio: Room, Ktor e a fila de pendências, montados pelo DataContainer.
// Sem Hilt nem WorkManager: esses são do :app. As features não dependem deste módulo.
plugins {
    alias(libs.plugins.desafio.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room)
}

android {
    namespace = "br.com.eduardoamaral.desafiointelbras.core.data"
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

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(project(":core:testing"))
    testImplementation(libs.ktor.client.mock)

    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
