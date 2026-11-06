package br.com.eduardoamaral.desafiointelbras.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Tira o foco (e fecha o teclado) quando o usuário toca nesta área.
 *
 * Observa o toque na fase Initial e não consome o evento: o clique continua chegando
 * aos filhos (chip, item da lista, menu...). Por isso funciona mesmo sobre áreas clicáveis.
 * Aplicar só nas áreas FORA do campo que deve perder o foco.
 *
 * Implementado com Modifier.Node (e não como função @Composable): é a forma recomendada
 * para modificadores, não depende de recomposição e pode ser usado fora de um Composable.
 */
fun Modifier.clearFocusOnTap(): Modifier = this then ClearFocusOnTapElement

private data object ClearFocusOnTapElement : ModifierNodeElement<ClearFocusOnTapNode>() {
    override fun create() = ClearFocusOnTapNode()

    override fun update(node: ClearFocusOnTapNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "clearFocusOnTap"
    }
}

private class ClearFocusOnTapNode :
    DelegatingNode(),
    CompositionLocalConsumerModifierNode {

    init {
        delegate(
            SuspendingPointerInputModifierNode {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (waitForUpOrCancellation(pass = PointerEventPass.Initial) != null) {
                        // Lido na hora do toque: sempre o FocusManager atual da tela.
                        currentValueOf(LocalFocusManager).clearFocus()
                    }
                }
            },
        )
    }
}
