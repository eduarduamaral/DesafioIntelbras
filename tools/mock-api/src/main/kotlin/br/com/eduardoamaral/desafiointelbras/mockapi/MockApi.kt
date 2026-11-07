package br.com.eduardoamaral.desafiointelbras.mockapi

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

/** Prefixo das rotas de controle (falhas, atraso, reset). Não pedem token. */
const val CONTROL_PATH = "/_mock"

/** Estado do servidor: os dois recursos e a simulação de falhas. */
class MockApiState(
    val videos: ResourceStore<VideoDevice> = ResourceStore(::seedVideos),
    val alarms: ResourceStore<AlarmCentral> = ResourceStore(::seedAlarms),
    val simulation: Simulation = Simulation(),
) {
    fun reset() {
        videos.reset()
        alarms.reset()
        simulation.reset()
    }
}

/**
 * Imita o contrato do servidor do desafio:
 * - GET lista em { count, data }; POST, PATCH /{id}, DELETE /{id} e GET /{id}.
 * - 403 sem token (ou com token diferente de [token], quando informado).
 * - 400 com a lista de mensagens, 404 para id desconhecido e 409 para serial/MAC repetido.
 */
fun Application.mockApi(token: String, state: MockApiState = MockApiState()) {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                // statusCode e error do 400 têm valor padrão e precisam ir na resposta.
                encodeDefaults = true
            },
        )
    }
    install(CallLogging) {
        format { call -> "${call.request.httpMethod.value} ${call.request.uri} -> ${call.response.status()}" }
    }
    install(bearerAuth(token))
    install(simulated(state.simulation))

    routing {
        resource("video-devices", state.videos, "serial", ::validateVideo, VideoDeviceBody::toDevice)
        resource("alarm-centrals", state.alarms, "macAddress", ::validateAlarm, AlarmCentralBody::toDevice)
        controlRoutes(state)
    }
}

private fun isControl(call: ApplicationCall) = call.request.path().startsWith(CONTROL_PATH)

private fun bearerAuth(expected: String) = createApplicationPlugin("BearerAuth") {
    onCall { call ->
        if (isControl(call)) return@onCall
        val sent = call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()
        val accepted = !sent.isNullOrBlank() && (expected.isBlank() || sent == expected)
        if (!accepted) call.respondError(HttpStatusCode.Forbidden, "Forbidden resource")
    }
}

private fun simulated(simulation: Simulation) = createApplicationPlugin("Simulation") {
    onCall { call ->
        if (isControl(call)) return@onCall
        if (simulation.delayMillis > 0) delay(simulation.delayMillis)
        simulation.nextFailure()?.let { call.respondError(it, "Simulated failure") }
    }
}

/** As 5 rotas de um recurso. Só os tipos e a validação mudam entre câmeras e alarmes. */
private inline fun <reified B : Any, reified T : Stored> Route.resource(
    path: String,
    store: ResourceStore<T>,
    uniqueField: String,
    crossinline validate: (B) -> List<String>,
    crossinline build: (body: B, id: String) -> T,
) {
    route(path) {
        get {
            val items = store.all()
            call.respond(ListResponse(items.size, items))
        }
        get("{id}") {
            val item = store.get(call.idParameter())
            if (item == null) call.respondNotFound() else call.respond(item)
        }
        post {
            val body = call.receive<B>()
            val errors = validate(body)
            if (errors.isEmpty()) {
                call.respondResult(store.create { id -> build(body, id) }, uniqueField, HttpStatusCode.Created)
            } else {
                call.respondInvalid(errors)
            }
        }
        patch("{id}") {
            val body = call.receive<B>()
            val errors = validate(body)
            if (errors.isEmpty()) {
                call.respondResult(store.update(call.idParameter()) { id -> build(body, id) }, uniqueField)
            } else {
                call.respondInvalid(errors)
            }
        }
        delete("{id}") {
            if (store.delete(call.idParameter())) call.respond(HttpStatusCode.OK) else call.respondNotFound()
        }
    }
}

fun ApplicationCall.idParameter(): String = parameters["id"].orEmpty()

suspend fun ApplicationCall.respondInvalid(errors: List<String>) =
    respond(HttpStatusCode.BadRequest, ValidationErrorResponse(errors))

suspend inline fun <reified T : Any> ApplicationCall.respondResult(
    result: StoreResult<T>,
    uniqueField: String,
    success: HttpStatusCode = HttpStatusCode.OK,
) = when (result) {
    is StoreResult.Ok -> respond(success, result.value)
    StoreResult.NotFound -> respondNotFound()
    StoreResult.Conflict -> respondError(HttpStatusCode.Conflict, "$uniqueField already exists")
}

suspend fun ApplicationCall.respondNotFound() = respondError(HttpStatusCode.NotFound, "Device not found")

suspend fun ApplicationCall.respondError(status: HttpStatusCode, message: String) =
    respond(status, ErrorResponse(status.value, message, status.description))
