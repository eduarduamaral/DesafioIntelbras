// Base visual compartilhada pelas features: tema, componentes, mensagens de erro e o
// barramento de Snackbars. Conhece o domínio (para traduzir erros), nunca os dados.
plugins {
    alias(libs.plugins.desafio.android.library)
    alias(libs.plugins.desafio.android.compose)
    alias(libs.plugins.desafio.hilt)
}

android {
    namespace = "br.com.eduardoamaral.desafiointelbras.core.ui"
}

dependencies {
    api(project(":core:domain"))

    // api: os componentes expõem tipos do Compose (Modifier, ImageVector...) para as features.
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
}
