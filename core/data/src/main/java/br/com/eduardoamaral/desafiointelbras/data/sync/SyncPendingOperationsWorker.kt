package br.com.eduardoamaral.desafiointelbras.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import br.com.eduardoamaral.desafiointelbras.domain.model.PendingSyncResult
import br.com.eduardoamaral.desafiointelbras.domain.repository.DeviceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Envia a fila de operações pendentes quando há conexão.
 *
 * Fino de propósito: toda a regra (ordem, classificação de erros, desfazer) fica no
 * repositório, que é testado com fakes. O worker só traduz o resultado para o WorkManager.
 * O repositório usa o mesmo Mutex do refresh, então os dois nunca rodam ao mesmo tempo.
 */
@HiltWorker
class SyncPendingOperationsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: DeviceRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = repository.syncPendingOperations().toWorkResult()
}

/**
 * - DONE: terminou.
 * - RETRY_LATER: o WorkManager tenta de novo com backoff exponencial.
 * - BLOCKED (token inválido): encerra sem descartar nada. Tentar de novo em loop não
 *   resolve um token inválido; a fila volta a ser enviada no próximo agendamento
 *   (abrir o app, puxar para atualizar ou fazer outra alteração).
 */
internal fun PendingSyncResult.toWorkResult(): ListenableWorker.Result = when (this) {
    PendingSyncResult.DONE -> ListenableWorker.Result.success()
    PendingSyncResult.RETRY_LATER -> ListenableWorker.Result.retry()
    PendingSyncResult.BLOCKED -> ListenableWorker.Result.success()
}
