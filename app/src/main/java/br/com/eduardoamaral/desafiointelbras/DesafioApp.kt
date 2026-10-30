package br.com.eduardoamaral.desafiointelbras

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import br.com.eduardoamaral.desafiointelbras.data.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Configura o WorkManager com a fábrica do Hilt, para os workers receberem dependências
 * por injeção (o SyncPendingOperationsWorker recebe o repositório). Para isso, a
 * inicialização automática do WorkManager é desligada no AndroidManifest.
 */
@HiltAndroidApp
class DesafioApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncScheduler: SyncScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Envia o que ficou pendente de uma sessão anterior (ex.: app fechado offline).
        // Com a fila vazia, o worker termina na hora.
        syncScheduler.schedule()
    }
}
