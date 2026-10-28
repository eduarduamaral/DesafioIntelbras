package br.com.eduardoamaral.desafiointelbras.data.remote

import br.com.eduardoamaral.desafiointelbras.domain.model.AppError
import br.com.eduardoamaral.desafiointelbras.domain.model.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
        Outcome.Failure(httpCodeToError(e.code(), e.response()?.errorBody()?.string()))
    } catch (e: SerializationException) {
        Outcome.Failure(AppError.Unknown)
    }

internal fun httpCodeToError(code: Int, errorBody: String? = null): AppError = when (code) {
    401, 403 -> AppError.Unauthorized
    404 -> AppError.NotFound
    408 -> AppError.Timeout
    409 -> AppError.Conflict
    400, 422 -> AppError.InvalidData(extractServerMessage(errorBody))
    else -> AppError.Server(code)
}

/**
 * A API (NestJS) responde erros de validação assim:
 * { "statusCode": 400, "error": "Bad Request", "message": ["serial must be ...", "..."] }
 * "message" pode ser uma lista ou um texto. Qualquer outro formato devolve null.
 */
internal fun extractServerMessage(errorBody: String?): String? {
    if (errorBody.isNullOrBlank()) return null
    val json = runCatching { Json.parseToJsonElement(errorBody) }.getOrNull() as? JsonObject ?: return null
    val text = when (val message = json["message"]) {
        is JsonArray -> message.mapNotNull { (it as? JsonPrimitive)?.content }.joinToString("; ")
        is JsonPrimitive -> message.content
        else -> null
    }
    return text?.takeIf { it.isNotBlank() }
}
