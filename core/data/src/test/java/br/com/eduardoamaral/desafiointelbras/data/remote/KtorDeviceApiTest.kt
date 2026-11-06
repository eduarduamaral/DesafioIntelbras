package br.com.eduardoamaral.desafiointelbras.data.remote

import br.com.eduardoamaral.desafiointelbras.data.remote.dto.AlarmCentralDto
import br.com.eduardoamaral.desafiointelbras.data.remote.dto.ListResponse
import br.com.eduardoamaral.desafiointelbras.data.remote.dto.VideoDeviceRequest
import br.com.eduardoamaral.desafiointelbras.domain.model.AppError
import br.com.eduardoamaral.desafiointelbras.domain.model.Outcome
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O cliente HTTP de verdade (createHttpClient + KtorDeviceApi) sobre um MockEngine:
 * confere URL, método, token, corpo enviado e como os status de erro chegam ao app.
 */
class KtorDeviceApiTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun api(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): DeviceApi {
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        return KtorDeviceApi(createHttpClient(engine, ApiConfig(BASE_URL, TOKEN)))
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun `listagem usa a URL base, o token e le o envelope count-data`() = runTest {
        // "novo" não existe no DTO: o app tem que ignorar campos que a API acrescentar.
        val central = """{"id":"a1","name":"Central","macAddress":"1A2B3C4D5E6F","password":"1234","novo":true}"""
        val api = api { json("""{"count":1,"data":[$central]}""") }

        val result = api.getAlarmCentrals()

        assertEquals(ListResponse(1, listOf(AlarmCentralDto("a1", "Central", "1A2B3C4D5E6F", "1234"))), result)
        val request = requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("${BASE_URL}alarm-centrals", request.url.toString())
        assertEquals("Bearer $TOKEN", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `edicao envia PATCH com o corpo em JSON`() = runTest {
        val api = api { json("""{"id":"v1","name":"Portão","serial":"ABC","username":"admin","password":"1234"}""") }

        api.updateVideoDevice("v1", VideoDeviceRequest("Portão", "ABC", "admin", "1234"))

        val request = requests.single()
        assertEquals(HttpMethod.Patch, request.method)
        assertEquals("${BASE_URL}video-devices/v1", request.url.toString())
        assertEquals("application/json", request.body.contentType?.withoutParameters()?.toString())
        val sent = request.body.toByteArray().decodeToString()
        assertTrue(sent, sent.contains("\"serial\":\"ABC\""))
    }

    @Test
    fun `status de erro chega como HttpStatusException com o corpo`() = runTest {
        val api = api { json("""{"statusCode":409,"message":"serial already exists"}""", HttpStatusCode.Conflict) }

        val result = safeApiCall { api.createVideoDevice(VideoDeviceRequest("Portão", "ABC", "admin", "1234")) }

        assertEquals(Outcome.Failure(AppError.Conflict), result)
    }

    @Test
    fun `400 leva a mensagem do servidor ate o app`() = runTest {
        val api = api { json("""{"statusCode":400,"message":["macAddress must be hex"]}""", HttpStatusCode.BadRequest) }

        val result = safeApiCall { api.getAlarmCentrals() }

        assertEquals(Outcome.Failure(AppError.InvalidData("macAddress must be hex")), result)
    }

    @Test
    fun `remocao aceita resposta sem corpo`() = runTest {
        val api = api { respond("", HttpStatusCode.NoContent) }

        api.deleteAlarmCentral("a1")

        assertEquals(HttpMethod.Delete, requests.single().method)
        assertEquals("${BASE_URL}alarm-centrals/a1", requests.single().url.toString())
    }

    private companion object {
        // Com barra no fim, como a URL real (API_BASE_URL).
        const val BASE_URL = "http://api.teste/"
        const val TOKEN = "token-de-teste"
    }
}
