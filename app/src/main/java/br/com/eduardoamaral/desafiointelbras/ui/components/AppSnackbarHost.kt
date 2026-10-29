package br.com.eduardoamaral.desafiointelbras.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Erros ficam mais tempo na tela e ganham botão de fechar. */
data class AppSnackbarVisuals(
    override val message: String,
    val isError: Boolean,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = isError,
    override val duration: SnackbarDuration =
        if (isError) SnackbarDuration.Long else SnackbarDuration.Short,
) : SnackbarVisuals

/**
 * Snackbar do guia: fundo escuro (#1A1B1D) e texto claro (#F5EFF7). As cores vêm de
 * inverseSurface/inverseOnSurface no tema, que é o padrão do Material 3 para Snackbar.
 */
@Composable
fun AppSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        Snackbar(snackbarData = data, shape = MaterialTheme.shapes.extraSmall)
    }
}
