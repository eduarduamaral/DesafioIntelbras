package br.com.eduardoamaral.desafiointelbras.ui.common

import br.com.eduardoamaral.desafiointelbras.R
import br.com.eduardoamaral.desafiointelbras.domain.model.AppError
import org.junit.Assert.assertEquals
import org.junit.Test

/** Como um erro do domínio vira o texto do Snackbar: regra da tela, por isso fica no app. */
class AppErrorMessagesTest {

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
