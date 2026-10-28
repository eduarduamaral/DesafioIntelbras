package br.com.eduardoamaral.desafiointelbras.data.remote

import br.com.eduardoamaral.desafiointelbras.R
import br.com.eduardoamaral.desafiointelbras.domain.model.AppError
import br.com.eduardoamaral.desafiointelbras.domain.model.Outcome
import br.com.eduardoamaral.desafiointelbras.ui.common.UserMessage
import br.com.eduardoamaral.desafiointelbras.ui.common.toUserMessage
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class SafeApiCallTest {

    @Test
    fun `400 traz a lista de mensagens do servidor`() = runTest {
        val body = """{"statusCode":400,"error":"Bad Request","message":["serial must be uppercase","password too short"]}"""

        val result = safeApiCall { throw HttpException(Response.error<Any>(400, body.toResponseBody())) }

        assertEquals(
            Outcome.Failure(AppError.InvalidData("serial must be uppercase; password too short")),
            result,
        )
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

    @Test
    fun `snackbar mostra o motivo do servidor quando existe`() {
        assertEquals(
            UserMessage(R.string.error_invalid_data_detail, isError = true, formatArgs = listOf("Invalid MAC")),
            AppError.InvalidData("Invalid MAC").toUserMessage(),
        )
        assertEquals(
            UserMessage(R.string.error_invalid_data, isError = true),
            AppError.InvalidData(null).toUserMessage(),
        )
    }
}
