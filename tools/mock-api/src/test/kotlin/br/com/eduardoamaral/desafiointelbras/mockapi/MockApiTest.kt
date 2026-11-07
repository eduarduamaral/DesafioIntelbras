package br.com.eduardoamaral.desafiointelbras.mockapi

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** O contrato que o app espera do servidor, conferido contra a API falsa. */
class MockApiTest {

    private fun mockApiTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { mockApi(token = TOKEN) }
        block()
    }

    private fun HttpRequestBuilder.json(body: String) {
        bearerAuth(TOKEN)
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    @Test
    fun `sem token ou com token errado responde 403`() = mockApiTest {
        assertEquals(HttpStatusCode.Forbidden, client.get("/video-devices").status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/video-devices") { bearerAuth("outro") }.status)
    }

    @Test
    fun `lista vem no envelope count-data`() = mockApiTest {
        val response = client.get("/alarm-centrals") { bearerAuth(TOKEN) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body, body.startsWith("""{"count":1,"data":[{"id":"seed-alarm-1""""))
    }

    @Test
    fun `serial repetido responde 409`() = mockApiTest {
        val response = client.post("/video-devices") {
            json("""{"name":"Outra","serial":"abc123def4567","username":"a","password":"b"}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `400 traz a lista de mensagens no formato do NestJS`() = mockApiTest {
        val response = client.post("/alarm-centrals") { json("""{"name":"","macAddress":"1A:2B","password":"12"}""") }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertTrue(body, body.contains("\"name should not be empty\""))
        assertTrue(body, body.contains("macAddress must be 12 hexadecimal characters"))
        assertTrue(body, body.contains("password must have 4 or 6 digits"))
    }

    @Test
    fun `cria e remove, e remover de novo responde 404`() = mockApiTest {
        val created = client.post("/video-devices") {
            json("""{"name":"Portão","serial":"NOVO123","username":"admin","password":"1234"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val id = requireNotNull(Regex("\"id\":\"([^\"]+)\"").find(created.bodyAsText())).groupValues[1]

        assertEquals(HttpStatusCode.OK, client.delete("/video-devices/$id") { bearerAuth(TOKEN) }.status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/video-devices/$id") { bearerAuth(TOKEN) }.status)
    }

    @Test
    fun `falha simulada vale so para as proximas requisicoes`() = mockApiTest {
        client.post("$CONTROL_PATH/fail?status=503&count=1")

        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/video-devices") { bearerAuth(TOKEN) }.status)
        assertEquals(HttpStatusCode.OK, client.get("/video-devices") { bearerAuth(TOKEN) }.status)
    }

    @Test
    fun `dados salvos sobrevivem a reiniciar o servidor`() {
        val file = File.createTempFile("mock-api", ".json").apply { delete() }
        try {
            val first = MockApiState(file)
            first.alarms.create { id -> AlarmCentral(id, "Nova", "AABBCCDDEEFF", "1234") }

            val restarted = MockApiState(file)

            assertEquals(listOf("Central do escritório", "Nova"), restarted.alarms.all().map { it.name })
        } finally {
            file.delete()
        }
    }

    private companion object {
        const val TOKEN = "token-de-teste"
    }
}
