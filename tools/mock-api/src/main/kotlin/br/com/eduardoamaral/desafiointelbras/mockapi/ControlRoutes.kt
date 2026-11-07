package br.com.eduardoamaral.desafiointelbras.mockapi

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

private const val DEFAULT_FAIL_STATUS = 500

private val HELP = """
    API falsa do Desafio Intelbras. Rotas de controle (sem token):

      POST /_mock/fail?status=500&count=3   as próximas 3 requisições respondem 500
      POST /_mock/delay?ms=20000            responde cada requisição com 20 s de atraso
      POST /_mock/reset                     volta aos dados iniciais e desliga falhas e atraso

    Exemplo: curl -X POST "http://localhost:8080/_mock/fail?status=503&count=2"
""".trimIndent()

fun Route.controlRoutes(state: MockApiState) {
    route(CONTROL_PATH) {
        get { call.respondText(HELP) }
        post("fail") {
            val status = HttpStatusCode.fromValue(call.parameters["status"]?.toIntOrNull() ?: DEFAULT_FAIL_STATUS)
            val count = call.parameters["count"]?.toIntOrNull() ?: 1
            state.simulation.failNext(status, count)
            call.respondText("As próximas $count requisições respondem ${status.value}.")
        }
        post("delay") {
            val millis = call.parameters["ms"]?.toLongOrNull() ?: 0
            state.simulation.delay(millis)
            call.respondText("Atraso de $millis ms em cada requisição.")
        }
        post("reset") {
            state.reset()
            call.respondText("Dados iniciais restaurados; falhas e atraso desligados.")
        }
    }
}
