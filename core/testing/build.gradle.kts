// Fakes e utilitários de teste que dependem só do domínio (ViewModels, features).
// Os fakes da camada de dados (banco, API) ficam nos testes do próprio :core:data.
plugins {
    alias(libs.plugins.desafio.jvm.library)
}

dependencies {
    api(project(":core:domain"))
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
}
