package br.com.eduardoamaral.desafiointelbras.ui.common

import androidx.annotation.StringRes
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

data class UserMessage(
    @param:StringRes val textRes: Int,
    val isError: Boolean = false,
    /** Valores para os %1$s da string. */
    val formatArgs: List<Any> = emptyList(),
)

/**
 * Canal único de Snackbars do app. Os ViewModels publicam e a raiz da UI exibe.
 * Resolve o caso "salvei no formulário e voltei pro dashboard": a mensagem sobrevive à navegação.
 * Channel (e não SharedFlow) porque cada mensagem deve ser consumida exatamente uma vez.
 */
@Singleton
class UserMessageBus @Inject constructor() {
    private val channel = Channel<UserMessage>(Channel.BUFFERED)
    val messages: Flow<UserMessage> = channel.receiveAsFlow()

    fun send(message: UserMessage) {
        channel.trySend(message)
    }
}
