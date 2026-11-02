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
// A exceção vira um AppError de propósito: o domínio só precisa saber o tipo da falha, e o
// detalhe técnico já aparece no log do OkHttp (tag DesafioHttp) nos builds de debug.
@Suppress("SwallowedException")
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
    HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN -> AppError.Unauthorized
    HttpStatus.NOT_FOUND -> AppError.NotFound
    HttpStatus.REQUEST_TIMEOUT -> AppError.Timeout
    HttpStatus.CONFLICT -> AppError.Conflict
    HttpStatus.BAD_REQUEST, HttpStatus.UNPROCESSABLE_ENTITY -> AppError.InvalidData(extractServerMessage(errorBody))
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

private object HttpStatus {
    const val BAD_REQUEST = 400
    const val UNAUTHORIZED = 401
    const val FORBIDDEN = 403
    const val NOT_FOUND = 404
    const val REQUEST_TIMEOUT = 408
    const val CONFLICT = 409
    const val UNPROCESSABLE_ENTITY = 422
}
