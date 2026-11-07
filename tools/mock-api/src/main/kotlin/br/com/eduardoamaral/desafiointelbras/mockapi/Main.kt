package br.com.eduardoamaral.desafiointelbras.mockapi

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer

private const val DEFAULT_PORT = 8080

/**
 * Sobe a API falsa. Escuta em todas as interfaces, para atender:
 * - o emulador Android em http://10.0.2.2:8080/ (o 10.0.2.2 é o computador, visto de dentro dele);
 * - o simulador do iOS em http://localhost:8080/.
 */
fun main() {
    val port = System.getenv("MOCK_API_PORT")?.toIntOrNull() ?: DEFAULT_PORT
    val token = System.getenv("API_TOKEN").orEmpty()
    println(
        """
        API falsa em http://localhost:$port/  (emulador Android: http://10.0.2.2:$port/)
        Token: ${if (token.isBlank()) "qualquer Bearer é aceito" else "o API_TOKEN do local.properties"}
        Falhas e atraso: GET http://localhost:$port$CONTROL_PATH
        """.trimIndent(),
    )
    embeddedServer(CIO, port = port, host = "0.0.0.0") { mockApi(token) }.start(wait = true)
}
