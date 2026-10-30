package br.com.eduardoamaral.desafiointelbras.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Pede o envio da fila de pendências. Interface para o repositório ser testado sem WorkManager. */
interface SyncScheduler {
    fun schedule()
}

/**
 * Agenda o [SyncPendingOperationsWorker] com o WorkManager:
 * - só roda com rede (NetworkType.CONNECTED): offline, o sistema espera a conexão voltar,
 *   mesmo com o app fechado ou depois de reiniciar o aparelho;
 * - trabalho único (UNIQUE_WORK): nunca há dois envios em paralelo;
 * - APPEND_OR_REPLACE: se um envio já está rodando, o novo pedido roda logo depois dele,
 *   garantindo que uma operação enfileirada durante o envio não fique esquecida;
 * - backoff exponencial: em falha temporária, espera cada vez mais antes de tentar de novo.
 */
@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncScheduler {

    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<SyncPendingOperationsWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    companion object {
        const val UNIQUE_WORK = "sync-pending-operations"
        private const val BACKOFF_SECONDS = 15L
    }
}
