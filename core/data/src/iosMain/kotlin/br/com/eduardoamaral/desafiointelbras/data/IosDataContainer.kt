package br.com.eduardoamaral.desafiointelbras.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import br.com.eduardoamaral.desafiointelbras.data.local.AppDatabase
import br.com.eduardoamaral.desafiointelbras.data.network.IosNetworkMonitor
import br.com.eduardoamaral.desafiointelbras.data.remote.ApiConfig
import br.com.eduardoamaral.desafiointelbras.data.sync.IosSyncScheduler
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * As peças iOS da camada de dados, chamada uma vez pelo app em Swift:
 * - Room com o SQLite embutido, num arquivo em Application Support;
 * - Ktor com o engine Darwin (NSURLSession);
 * - NWPathMonitor para saber se há rede;
 * - um agendador que envia a fila quando há rede (o iOS não tem WorkManager).
 */
fun createIosDataContainer(apiConfig: ApiConfig, httpLog: ((String) -> Unit)? = null): DataContainer {
    val networkMonitor = IosNetworkMonitor()
    val syncScheduler = IosSyncScheduler()
    val database = Room.databaseBuilder<AppDatabase>(name = databasePath())
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
    return DataContainer(
        database = database,
        httpEngine = Darwin.create(),
        apiConfig = apiConfig,
        networkMonitor = networkMonitor,
        syncScheduler = syncScheduler,
        httpLog = httpLog,
    ).also { container ->
        // O repositório precisa do agendador e o agendador do repositório: liga depois.
        syncScheduler.start(container.repository, networkMonitor)
        // Envia o que ficou pendente de uma sessão anterior, como faz o app Android ao abrir.
        syncScheduler.schedule()
    }
}

/**
 * Application Support, e não Documents: o arquivo não aparece para o usuário. Fica fora do
 * backup do iCloud porque guarda as senhas dos dispositivos em texto puro (a API as devolve
 * assim), a mesma decisão tomada no Android (backup_rules.xml).
 */
@OptIn(ExperimentalForeignApi::class)
private fun databasePath(): String {
    val directory: NSURL = requireNotNull(
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        ),
    ) { "Pasta Application Support indisponível" }
    directory.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    return requireNotNull(directory.path) + "/" + AppDatabase.NAME
}
