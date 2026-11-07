package br.com.eduardoamaral.desafiointelbras.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** A classificação decide se a fila tenta de novo, pausa, confirma ou desfaz. */
class FailureKindTest {

    @Test
    fun `sem rede timeout 5xx e 429 sao temporarios`() {
        listOf(AppError.Network, AppError.Timeout, AppError.Server(500), AppError.Server(503), AppError.Server(429))
            .forEach { assertEquals(FailureKind.TRANSIENT, it.failureKind(), it.toString()) }
    }

    @Test
    fun `resposta ilegivel nao desfaz porque o servidor pode ter aplicado`() {
        assertEquals(FailureKind.TRANSIENT, AppError.Unknown.failureKind())
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
        listOf(AppError.Conflict, AppError.InvalidData("x"), AppError.Server(418))
            .forEach { assertEquals(FailureKind.REJECTED, it.failureKind(), it.toString()) }
    }
}
