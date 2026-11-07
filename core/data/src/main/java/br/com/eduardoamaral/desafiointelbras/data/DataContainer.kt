package br.com.eduardoamaral.desafiointelbras.data

import br.com.eduardoamaral.desafiointelbras.data.local.AppDatabase
import br.com.eduardoamaral.desafiointelbras.data.local.RoomDeviceLocalDataSource
import br.com.eduardoamaral.desafiointelbras.data.local.RoomPendingOperationsLocalDataSource
import br.com.eduardoamaral.desafiointelbras.data.remote.ApiConfig
import br.com.eduardoamaral.desafiointelbras.data.remote.ApiDeviceRemoteDataSource
import br.com.eduardoamaral.desafiointelbras.data.remote.KtorDeviceApi
import br.com.eduardoamaral.desafiointelbras.data.remote.createHttpClient
import br.com.eduardoamaral.desafiointelbras.data.repository.OfflineFirstDeviceRepository
import br.com.eduardoamaral.desafiointelbras.data.sync.SyncScheduler
import br.com.eduardoamaral.desafiointelbras.domain.network.NetworkMonitor
import br.com.eduardoamaral.desafiointelbras.domain.repository.DeviceRepository
import io.ktor.client.engine.HttpClientEngine

/**
 * Monta a camada de dados. É o único lugar que liga banco, API, fila e repositório, e serve
 * às duas plataformas: cada uma só entrega as peças que dependem dela (o banco, o engine
 * HTTP, o monitor de rede e o agendador do envio da fila).
 *
 * - Android: o Hilt do :app chama [createAndroidDataContainer] e repassa [repository] e
 *   [networkMonitor] às telas.
 * - iOS: o app chama a fábrica do iOS e usa as mesmas classes.
 *
 * Sem framework de injeção aqui de propósito: o Hilt só existe no Android.
 * Crie um só por app (o repositório guarda o Mutex e a fila em memória).
 */
class DataContainer internal constructor(
    database: AppDatabase,
    httpEngine: HttpClientEngine,
    apiConfig: ApiConfig,
    val networkMonitor: NetworkMonitor,
    syncScheduler: SyncScheduler,
    httpLog: ((String) -> Unit)? = null,
) {
    private val dao = database.deviceDao()

    val repository: DeviceRepository = OfflineFirstDeviceRepository(
        remote = ApiDeviceRemoteDataSource(KtorDeviceApi(createHttpClient(httpEngine, apiConfig, httpLog = httpLog))),
        local = RoomDeviceLocalDataSource(dao),
        outbox = RoomPendingOperationsLocalDataSource(dao),
        syncScheduler = syncScheduler,
    )
}
