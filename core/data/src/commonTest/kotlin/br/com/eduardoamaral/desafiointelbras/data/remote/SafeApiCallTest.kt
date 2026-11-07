package br.com.eduardoamaral.desafiointelbras.data.remote

import br.com.eduardoamaral.desafiointelbras.domain.model.AppError
import br.com.eduardoamaral.desafiointelbras.domain.model.Outcome
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SafeApiCallTest {

    @Test
    fun `400 traz a lista de mensagens do servidor`() = runTest {
        val body = """
            {"statusCode":400,"error":"Bad Request","message":["serial must be uppercase","password too short"]}
        """.trimIndent()

        val result = safeApiCall { throw HttpStatusException(400, body) }

        assertEquals(
            Outcome.Failure(AppError.InvalidData("serial must be uppercase; password too short")),
            result,
        )
    }

    @Test
    fun `timeout e falta de rede viram erros diferentes`() = runTest {
        assertEquals(
            Outcome.Failure(AppError.Timeout),
            safeApiCall { throw HttpRequestTimeoutException("video-devices", 15_000L) },
        )
        assertEquals(Outcome.Failure(AppError.Network), safeApiCall { throw IOException("offline") })
    }

    @Test
    fun `mensagem do servidor como texto simples`() {
        assertEquals("Invalid MAC", extractServerMessage("""{"message":"Invalid MAC"}"""))
    }

    @Test
    fun `corpo vazio ou fora do formato nao quebra`() {
        assertNull(extractServerMessage(null))
        assertNull(extractServerMessage(""))
        assertNull(extractServerMessage("<html>Bad Request</html>"))
        assertNull(extractServerMessage("""{"error":"Bad Request"}"""))
        assertEquals(AppError.InvalidData(null), httpCodeToError(400, "nada"))
    }
}
