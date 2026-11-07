package br.com.eduardoamaral.desafiointelbras.mockapi

import io.ktor.http.HttpStatusCode

/**
 * Falhas e lentidão sob demanda, para testar no app o que o servidor real não deixava
 * provocar: fila offline com erro 500, timeout, token recusado...
 * Controlado pelas rotas /_mock (ver [controlRoutes]).
 */
class Simulation {
    private var failStatus: HttpStatusCode? = null
    private var remainingFailures = 0

    @Volatile
    var delayMillis: Long = 0
        private set

    /** As próximas [count] requisições aos recursos respondem [status]. */
    @Synchronized
    fun failNext(status: HttpStatusCode, count: Int) {
        failStatus = status
        remainingFailures = count
    }

    fun delay(millis: Long) {
        delayMillis = millis
    }

    /** Consome uma falha programada, se houver. */
    @Synchronized
    fun nextFailure(): HttpStatusCode? {
        if (remainingFailures <= 0) return null
        remainingFailures--
        return failStatus
    }

    @Synchronized
    fun reset() {
        failStatus = null
        remainingFailures = 0
        delayMillis = 0
    }
}
