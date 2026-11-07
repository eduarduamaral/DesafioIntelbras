package br.com.eduardoamaral.desafiointelbras.mockapi

import kotlinx.serialization.Serializable

/** Registro guardado em memória. [uniqueKey] é o campo que o servidor não deixa repetir (409). */
interface Stored {
    val id: String
    val uniqueKey: String
}

@Serializable
data class VideoDevice(
    override val id: String,
    val name: String,
    val serial: String,
    val username: String,
    val password: String,
) : Stored {
    override val uniqueKey: String get() = serial
}

@Serializable
data class AlarmCentral(
    override val id: String,
    val name: String,
    val macAddress: String,
    val password: String,
) : Stored {
    override val uniqueKey: String get() = macAddress
}

// Corpos de POST/PATCH com tudo opcional: o que faltar vira 400, como no servidor real.

@Serializable
data class VideoDeviceBody(
    val name: String? = null,
    val serial: String? = null,
    val username: String? = null,
    val password: String? = null,
)

@Serializable
data class AlarmCentralBody(
    val name: String? = null,
    val macAddress: String? = null,
    val password: String? = null,
)

// Chamadas só depois da validação, que garante os campos preenchidos.
fun VideoDeviceBody.toDevice(id: String) =
    VideoDevice(id, name.orEmpty(), serial.orEmpty(), username.orEmpty(), password.orEmpty())

fun AlarmCentralBody.toDevice(id: String) = AlarmCentral(id, name.orEmpty(), macAddress.orEmpty(), password.orEmpty())

@Serializable
data class ListResponse<T>(val count: Int, val data: List<T>)

private const val BAD_REQUEST = 400

/** Formato de erro do NestJS. No 400 a "message" é uma lista; nos outros, um texto. */
@Serializable
data class ErrorResponse(val statusCode: Int, val message: String, val error: String)

@Serializable
data class ValidationErrorResponse(
    val message: List<String>,
    val statusCode: Int = BAD_REQUEST,
    val error: String = "Bad Request",
)
