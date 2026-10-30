package br.com.eduardoamaral.desafiointelbras.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** A classificação decide se a fila tenta de novo, pausa, confirma ou desfaz. */
class FailureKindTest {

    @Test
    fun `sem rede timeout 5xx e 429 sao temporarios`() {
        listOf(AppError.Network, AppError.Timeout, AppError.Server(500), AppError.Server(503), AppError.Server(429))
            .forEach { assertEquals(it.toString(), FailureKind.TRANSIENT, it.failureKind()) }
    }

    @Test
    fun `token invalido pausa em vez de descartar`() {
        assertEquals(FailureKind.AUTH, AppError.Unauthorized.failureKind())
    }

    @Test
    fun `404 significa que o objetivo ja foi atingido`() {
        assertEquals(FailureKind.ALREADY_DONE, AppError.NotFound.failureKind())
    }

    @Test
    fun `recusas definitivas desfazem a mudanca local`() {
        listOf(AppError.Conflict, AppError.InvalidData("x"), AppError.Server(418), AppError.Unknown)
            .forEach { assertEquals(it.toString(), FailureKind.REJECTED, it.failureKind()) }
    }
}
