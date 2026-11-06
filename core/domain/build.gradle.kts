// Kotlin puro, sem Android: modelos, regras e contratos. Não depende de nenhum outro módulo.
plugins {
    alias(libs.plugins.desafio.jvm.library)
}

dependencies {
    // Só pelo Flow nas interfaces (DeviceRepository, NetworkMonitor).
    api(libs.kotlinx.coroutines.core)
}
