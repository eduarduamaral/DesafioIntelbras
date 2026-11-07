// Fakes e utilitários de teste que dependem só do domínio, compartilhados pelos testes das
// features (Android) e do :core:data (Android e iOS). Os fakes da camada de dados (banco,
// API) ficam nos testes do próprio :core:data.
plugins {
    alias(libs.plugins.desafio.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            api(libs.kotlinx.coroutines.test)
        }
        // MainDispatcherRule é uma regra da JUnit 4: só existe para os testes na JVM.
        jvmMain.dependencies {
            api(libs.junit)
        }
    }
}
