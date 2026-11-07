package br.com.eduardoamaral.desafiointelbras.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Onde está a API e com que token falar com ela. Quem monta é o app (Android) ou o host iOS. */
data class ApiConfig(val baseUrl: String, val token: String)

/** O servidor pode mandar campos novos ou nulos sem quebrar o app. */
val ApiJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

private const val TIMEOUT_MILLIS = 15_000L

/**
 * Cliente HTTP da API. O engine vem de fora: OkHttp no Android, Darwin no iOS e
 * MockEngine nos testes; o resto da configuração é a mesma em todos.
 *
 * - URL base e `Authorization: Bearer <token>` em toda requisição.
 * - JSON com kotlinx.serialization.
 * - Status fora de 2xx vira [HttpStatusException], com o corpo (a API explica o 400 nele).
 * - [timeoutMillis] para conectar, para cada leitura e para a requisição inteira (menor nos testes).
 * - [httpLog], se informado, recebe requisições e respostas, com o token mascarado.
 */
fun createHttpClient(
    engine: HttpClientEngine,
    config: ApiConfig,
    json: Json = ApiJson,
    httpLog: ((String) -> Unit)? = null,
    timeoutMillis: Long = TIMEOUT_MILLIS,
): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(ContentNegotiation) { json(json) }
    install(HttpTimeout) {
        connectTimeoutMillis = timeoutMillis
        requestTimeoutMillis = timeoutMillis
        socketTimeoutMillis = timeoutMillis
    }
    defaultRequest {
        url(config.baseUrl)
        header(HttpHeaders.Authorization, "Bearer ${config.token}")
    }
    HttpResponseValidator {
        validateResponse { response ->
            if (!response.status.isSuccess()) {
                throw HttpStatusException(response.status.value, response.bodyAsText())
            }
        }
    }
    if (httpLog != null) {
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) = httpLog(message)
            }
            level = LogLevel.BODY
            sanitizeHeader { it == HttpHeaders.Authorization }
        }
    }
}

/** Resposta HTTP fora de 2xx. Não depende do Ktor, então os fakes dos testes podem lançá-la. */
class HttpStatusException(val code: Int, val body: String? = null) : Exception("HTTP $code")
