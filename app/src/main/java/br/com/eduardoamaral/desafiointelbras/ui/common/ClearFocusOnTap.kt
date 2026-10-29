package br.com.eduardoamaral.desafiointelbras.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Tira o foco (e fecha o teclado) quando o usuário toca nesta área.
 *
 * Observa o toque na fase Initial e não consome o evento: o clique continua chegando
 * aos filhos (chip, item da lista, menu...). Por isso funciona mesmo sobre áreas clicáveis.
 * Aplicar só nas áreas FORA do campo que deve perder o foco.
 */
@Composable
fun Modifier.clearFocusOnTap(): Modifier {
    val focusManager = LocalFocusManager.current
    return this.pointerInput(focusManager) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (waitForUpOrCancellation(pass = PointerEventPass.Initial) != null) {
                focusManager.clearFocus()
            }
        }
    }
}
