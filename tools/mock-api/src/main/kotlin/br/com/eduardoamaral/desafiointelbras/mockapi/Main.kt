package br.com.eduardoamaral.desafiointelbras.mockapi

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import java.io.File

private const val DEFAULT_PORT = 8080

/**
 * Sobe a API falsa. Escuta em todas as interfaces, para atender:
 * - o emulador Android em http://10.0.2.2:8080/ (o 10.0.2.2 é o computador, visto de dentro dele);
 * - o simulador do iOS em http://localhost:8080/.
 */
fun main() {
    val port = System.getenv("MOCK_API_PORT")?.toIntOrNull() ?: DEFAULT_PORT
    val token = System.getenv("API_TOKEN").orEmpty()
    // Relativo à pasta do módulo (tools/mock-api), de onde o Gradle roda o servidor.
    val dataFile = File(System.getenv("MOCK_API_DATA") ?: "data.json").absoluteFile
    println(
        """
        API falsa em http://localhost:$port/  (emulador Android: http://10.0.2.2:$port/)
        Token: ${if (token.isBlank()) "qualquer Bearer é aceito" else "o API_TOKEN do local.properties"}
        Dados salvos em: $dataFile  (POST $CONTROL_PATH/reset volta aos dados iniciais)
        Falhas e atraso: GET http://localhost:$port$CONTROL_PATH
        """.trimIndent(),
    )
    embeddedServer(CIO, port = port, host = "0.0.0.0") { mockApi(token, MockApiState(dataFile)) }.start(wait = true)
}
