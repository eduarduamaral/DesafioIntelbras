package br.com.eduardoamaral.desafiointelbras.domain.model

/** Resultado de uma rodada de envio da fila de operações pendentes. */
enum class PendingSyncResult {
    /** Fila vazia: tudo foi enviado (ou desfeito, quando o servidor recusou). */
    DONE,

    /** Falha temporária (sem rede, timeout, servidor fora): tentar de novo mais tarde. */
    RETRY_LATER,

    /** Acesso negado (token): parar sem descartar nada; volta na próxima tentativa. */
    BLOCKED,
}

/** Como a fila reage a cada erro. Classificar evita tratar "sem rede" como "recusado". */
enum class FailureKind { TRANSIENT, AUTH, ALREADY_DONE, REJECTED }

/**
 * - TRANSIENT: rede, timeout, 5xx e 429. Tentar de novo; o usuário não perde nada.
 * - AUTH: token inválido. Pausar a fila; descartar as mudanças do usuário seria errado.
 * - ALREADY_DONE: 404 numa remoção. O item já não existe no servidor: objetivo atingido.
 * - REJECTED: o servidor recusou de vez (400, 409...). Desfazer a mudança local.
 */
fun AppError.failureKind(): FailureKind = when (this) {
    AppError.Network, AppError.Timeout -> FailureKind.TRANSIENT
    is AppError.Server ->
        if (code >= FIRST_SERVER_ERROR || code == TOO_MANY_REQUESTS) FailureKind.TRANSIENT else FailureKind.REJECTED
    AppError.Unauthorized -> FailureKind.AUTH
    AppError.NotFound -> FailureKind.ALREADY_DONE
    AppError.Conflict, is AppError.InvalidData, AppError.Unknown -> FailureKind.REJECTED
}

/** Acontecimentos da sincronização em segundo plano que o usuário precisa saber. */
sealed interface SyncEvent {
    /** O servidor recusou uma remoção feita offline; o dispositivo voltou para a lista. */
    data class DeleteRejected(val deviceName: String) : SyncEvent
}

private const val FIRST_SERVER_ERROR = 500 // 5xx: problema do servidor, não do pedido
private const val TOO_MANY_REQUESTS = 429
