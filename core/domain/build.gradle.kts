// Kotlin Multiplatform, sem Android: modelos, regras e contratos, compartilhados com o iOS.
// Não depende de nenhum outro módulo. O Android consome o alvo jvm.
plugins {
    alias(libs.plugins.desafio.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Só pelo Flow nas interfaces (DeviceRepository, NetworkMonitor).
            api(libs.kotlinx.coroutines.core)
        }
    }
}
