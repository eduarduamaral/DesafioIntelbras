package br.com.eduardoamaral.desafiointelbras.data

import android.content.Context
import androidx.room.Room
import br.com.eduardoamaral.desafiointelbras.data.local.AppDatabase
import br.com.eduardoamaral.desafiointelbras.data.network.ConnectivityNetworkMonitor
import br.com.eduardoamaral.desafiointelbras.data.remote.ApiConfig
import br.com.eduardoamaral.desafiointelbras.data.sync.SyncScheduler
import io.ktor.client.engine.okhttp.OkHttp

/**
 * As peças Android da camada de dados: Room no armazenamento do app, Ktor com OkHttp e o
 * ConnectivityManager. O agendador vem de fora porque o worker do WorkManager vive no :app.
 */
fun createAndroidDataContainer(
    context: Context,
    apiConfig: ApiConfig,
    syncScheduler: SyncScheduler,
    httpLog: ((String) -> Unit)? = null,
): DataContainer {
    val appContext = context.applicationContext
    return DataContainer(
        database = Room.databaseBuilder(appContext, AppDatabase::class.java, AppDatabase.NAME).build(),
        httpEngine = OkHttp.create(),
        apiConfig = apiConfig,
        networkMonitor = ConnectivityNetworkMonitor(appContext),
        syncScheduler = syncScheduler,
        httpLog = httpLog,
    )
}
