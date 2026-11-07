package br.com.eduardoamaral.desafiointelbras.data.sync

import br.com.eduardoamaral.desafiointelbras.domain.model.PendingSyncResult
import br.com.eduardoamaral.desafiointelbras.domain.network.NetworkMonitor
import br.com.eduardoamaral.desafiointelbras.domain.repository.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * O que o WorkManager faz no Android, de forma simples, enquanto o app está aberto:
 * - cada [schedule] pede uma rodada; pedidos durante uma rodada viram uma só, logo depois
 *   (como o APPEND_OR_REPLACE do Android);
 * - sem rede, espera ela voltar antes de tentar;
 * - falha temporária: tenta de novo com espera crescente (15 s, 30 s, 1 min... até 5 min);
 * - token recusado (BLOCKED): para até o próximo pedido.
 *
 * Com o app fechado nada roda; a fila é enviada na próxima abertura (ver createIosDataContainer).
 * Enviar em segundo plano exigiria o BGTaskScheduler, com permissões e agenda do sistema.
 */
internal class IosSyncScheduler(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : SyncScheduler {

    private val requests = Channel<Unit>(Channel.CONFLATED)

    override fun schedule() {
        requests.trySend(Unit)
    }

    fun start(repository: DeviceRepository, networkMonitor: NetworkMonitor) {
        scope.launch {
            requests.receiveAsFlow().collect { sendUntilSettled(repository, networkMonitor) }
        }
    }

    private suspend fun sendUntilSettled(repository: DeviceRepository, networkMonitor: NetworkMonitor) {
        var wait = FIRST_RETRY
        while (true) {
            networkMonitor.isOnline.first { it }
            if (repository.syncPendingOperations() != PendingSyncResult.RETRY_LATER) return
            delay(wait)
            wait = (wait * 2).coerceAtMost(MAX_RETRY)
        }
    }

    private companion object {
        val FIRST_RETRY: Duration = 15.seconds
        val MAX_RETRY: Duration = 5.minutes
    }
}
