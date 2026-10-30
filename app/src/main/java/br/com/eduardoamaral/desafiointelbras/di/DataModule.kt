package br.com.eduardoamaral.desafiointelbras.di

import br.com.eduardoamaral.desafiointelbras.data.local.DeviceLocalDataSource
import br.com.eduardoamaral.desafiointelbras.data.local.RoomDeviceLocalDataSource
import br.com.eduardoamaral.desafiointelbras.data.remote.DeviceRemoteDataSource
import br.com.eduardoamaral.desafiointelbras.data.remote.RetrofitDeviceRemoteDataSource
import br.com.eduardoamaral.desafiointelbras.data.network.ConnectivityNetworkMonitor
import br.com.eduardoamaral.desafiointelbras.data.network.NetworkMonitor
import br.com.eduardoamaral.desafiointelbras.data.repository.OfflineFirstDeviceRepository
import br.com.eduardoamaral.desafiointelbras.data.sync.SyncScheduler
import br.com.eduardoamaral.desafiointelbras.data.sync.WorkManagerSyncScheduler
import br.com.eduardoamaral.desafiointelbras.domain.repository.DeviceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindDeviceRepository(impl: OfflineFirstDeviceRepository): DeviceRepository

    @Binds
    abstract fun bindDeviceLocalDataSource(impl: RoomDeviceLocalDataSource): DeviceLocalDataSource

    @Binds
    abstract fun bindDeviceRemoteDataSource(impl: RetrofitDeviceRemoteDataSource): DeviceRemoteDataSource

    @Binds
    abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler

    @Binds
    abstract fun bindNetworkMonitor(impl: ConnectivityNetworkMonitor): NetworkMonitor
}
