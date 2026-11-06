package br.com.eduardoamaral.desafiointelbras.data.sync

import androidx.work.ListenableWorker.Result
import br.com.eduardoamaral.desafiointelbras.domain.model.PendingSyncResult
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * O worker só traduz o resultado do repositório para o WorkManager; a regra em si é
 * testada em OfflineFirstDeviceRepositoryTest. Aqui garantimos a tradução.
 */
class SyncPendingOperationsWorkerTest {

    @Test
    fun `fila enviada termina com sucesso`() {
        assertEquals(Result.success(), PendingSyncResult.DONE.toWorkResult())
    }

    @Test
    fun `falha temporaria pede nova tentativa com backoff`() {
        assertEquals(Result.retry(), PendingSyncResult.RETRY_LATER.toWorkResult())
    }

    @Test
    fun `token invalido encerra sem ficar tentando em loop`() {
        assertEquals(Result.success(), PendingSyncResult.BLOCKED.toWorkResult())
    }
}
