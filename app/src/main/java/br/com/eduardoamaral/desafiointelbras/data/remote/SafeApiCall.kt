package br.com.eduardoamaral.desafiointelbras.data.remote

import br.com.eduardoamaral.desafiointelbras.domain.model.AppError
import br.com.eduardoamaral.desafiointelbras.domain.model.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Executa uma chamada de rede e traduz exceções em [AppError].
 * CancellationException é relançada para não quebrar o cancelamento estruturado
 * (ex.: usuário saiu da tela no meio da requisição).
 */
suspend fun <T> safeApiCall(block: suspend () -> T): Outcome<T> =
    try {
        Outcome.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: SocketTimeoutException) {
        Outcome.Failure(AppError.Timeout)
    } catch (e: IOException) {
        Outcome.Failure(AppError.Network)
    } catch (e: HttpException) {
        Outcome.Failure(httpCodeToError(e.code()))
    } catch (e: SerializationException) {
        Outcome.Failure(AppError.Unknown)
    }

internal fun httpCodeToError(code: Int): AppError = when (code) {
    401, 403 -> AppError.Unauthorized
    404 -> AppError.NotFound
    408 -> AppError.Timeout
    409 -> AppError.Conflict
    400, 422 -> AppError.InvalidData
    else -> AppError.Server(code)
}
