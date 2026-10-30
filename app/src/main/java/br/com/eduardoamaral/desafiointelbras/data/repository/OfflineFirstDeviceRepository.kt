package br.com.eduardoamaral.desafiointelbras.data.repository

import br.com.eduardoamaral.desafiointelbras.data.local.DeviceLocalDataSource
import br.com.eduardoamaral.desafiointelbras.data.remote.DeviceRemoteDataSource
import br.com.eduardoamaral.desafiointelbras.domain.model.AppError
import br.com.eduardoamaral.desafiointelbras.domain.model.Device
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceFilter
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceInput
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceType
import br.com.eduardoamaral.desafiointelbras.domain.model.Outcome
import br.com.eduardoamaral.desafiointelbras.domain.repository.DeviceRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orquestra as duas fontes: a UI lê sempre da [local] (fonte única de verdade) e as escritas
 * passam pela [remote] antes de serem refletidas no banco. Não conhece Retrofit, DTOs nem Room.
 *
 * Room e Retrofit (funções suspend) já são main-safe, por isso não há withContext(IO) aqui.
 *
 * O [mutex] serializa as operações que escrevem no banco a partir da API. Sem ele, um refresh
 * iniciado antes de um "criar" poderia terminar depois e apagar o item recém-criado
 * (a lista do servidor ainda não o tinha).
 */
@Singleton
class OfflineFirstDeviceRepository @Inject constructor(
    private val remote: DeviceRemoteDataSource,
    private val local: DeviceLocalDataSource,
) : DeviceRepository {

    private val mutex = Mutex()

    override fun observeDevices(query: String, filter: DeviceFilter): Flow<List<Device>> =
        local.observeDevices(query.trim(), filter)

    override fun observeDevice(id: String): Flow<Device?> = local.observeDevice(id)

    override fun observeDeviceCount(): Flow<Int> = local.observeCount()

    /**
     * Busca os dois tipos em paralelo e sincroniza cada um de forma independente.
     * Se um falhar, o outro ainda é atualizado; o erro retornado é o da primeira falha.
     * Em qualquer falha, o banco local daquele tipo fica intacto (offline mostra o último sync).
     */
    override suspend fun refresh(): Outcome<Unit> = mutex.withLock {
        coroutineScope {
            val results = DeviceType.entries
                .map { type -> type to async { remote.fetchDevices(type) } }
                .map { (type, result) -> type to result.await() }

            results.forEach { (type, result) ->
                if (result is Outcome.Success) local.syncType(type, result.data)
            }
            results.firstNotNullOfOrNull { (_, result) -> result as? Outcome.Failure }
                ?: Outcome.Success(Unit)
        }
    }

    override suspend fun create(input: DeviceInput): Outcome<Device> = mutex.withLock {
        remote.create(input).alsoSaveLocally()
    }

    override suspend fun update(id: String, input: DeviceInput): Outcome<Device> = mutex.withLock {
        remote.update(id, input).alsoSaveLocally()
    }

    override suspend fun delete(id: String): Outcome<Unit> = mutex.withLock {
        // O tipo vem do banco local: ele decide qual endpoint chamar.
        val device = local.getDevice(id) ?: return@withLock Outcome.Failure(AppError.NotFound)
        when (val result = remote.delete(id, device.type)) {
            is Outcome.Success -> {
                local.delete(id)
                result
            }
            // Se o servidor já não tem o item, o estado desejado foi alcançado.
            is Outcome.Failure -> if (result.error == AppError.NotFound) {
                local.delete(id)
                Outcome.Success(Unit)
            } else {
                result
            }
        }
    }

    override suspend fun toggleFavorite(id: String) = local.toggleFavorite(id)

    /** Com sucesso, grava a resposta do servidor (que é a versão oficial) no banco local. */
    private suspend fun Outcome<Device>.alsoSaveLocally(): Outcome<Device> {
        if (this is Outcome.Success) local.upsert(data)
        return this
    }
}
