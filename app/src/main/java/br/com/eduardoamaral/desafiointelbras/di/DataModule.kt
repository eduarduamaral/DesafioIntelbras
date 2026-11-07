package br.com.eduardoamaral.desafiointelbras.di

import android.content.Context
import android.util.Log
import br.com.eduardoamaral.desafiointelbras.BuildConfig
import br.com.eduardoamaral.desafiointelbras.data.DataContainer
import br.com.eduardoamaral.desafiointelbras.data.createAndroidDataContainer
import br.com.eduardoamaral.desafiointelbras.data.remote.ApiConfig
import br.com.eduardoamaral.desafiointelbras.data.sync.SyncScheduler
import br.com.eduardoamaral.desafiointelbras.domain.network.NetworkMonitor
import br.com.eduardoamaral.desafiointelbras.domain.repository.DeviceRepository
import br.com.eduardoamaral.desafiointelbras.sync.WorkManagerSyncScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Liga a camada de dados ao Hilt. O :core:data não conhece o Hilt: ele entrega um
 * [DataContainer] pronto, e aqui só expomos o que as telas e o worker pedem.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    // Tag fixa para filtrar no Logcat: tag:DesafioHttp
    private const val HTTP_LOG_TAG = "DesafioHttp"

    @Provides
    @Singleton
    fun provideDataContainer(
        @ApplicationContext context: Context,
        syncScheduler: SyncScheduler,
    ): DataContainer = createAndroidDataContainer(
        context = context,
        // O token vem do local.properties (ou da variável API_TOKEN), nunca do repositório.
        apiConfig = ApiConfig(baseUrl = BuildConfig.API_BASE_URL, token = BuildConfig.API_TOKEN),
        syncScheduler = syncScheduler,
        httpLog = if (BuildConfig.DEBUG) { message -> Log.d(HTTP_LOG_TAG, message) } else null,
    )

    @Provides
    fun provideDeviceRepository(container: DataContainer): DeviceRepository = container.repository

    @Provides
    fun provideNetworkMonitor(container: DataContainer): NetworkMonitor = container.networkMonitor
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds
    abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler
}
