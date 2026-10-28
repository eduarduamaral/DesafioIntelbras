package br.com.eduardoamaral.desafiointelbras.domain.model

/**
 * Resultado de uma operação que pode falhar. Usamos um tipo próprio (em vez de kotlin.Result)
 * para que o erro seja sempre um [AppError] conhecido, e não uma Throwable qualquer.
 */
sealed interface Outcome<out T> {
    data class Success<T>(val data: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(data))
    is Outcome.Failure -> this
}

/** Erros que a UI sabe tratar. */
sealed interface AppError {
    data object Network : AppError
    data object Timeout : AppError
    /** A API responde 403 para token ausente/inválido. */
    data object Unauthorized : AppError
    data object NotFound : AppError
    /** 409: serial (vídeo) ou MAC (alarme) já cadastrado em outro dispositivo. */
    data object Conflict : AppError
    /** 400/422: o servidor recusou os dados enviados. */
    data object InvalidData : AppError
    data class Server(val code: Int) : AppError
    data object Unknown : AppError
}
