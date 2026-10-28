package br.com.eduardoamaral.desafiointelbras.data.repository

import br.com.eduardoamaral.desafiointelbras.data.local.DeviceLocalDataSource
import br.com.eduardoamaral.desafiointelbras.data.mapper.toDomain
import br.com.eduardoamaral.desafiointelbras.data.mapper.toRequest
import br.com.eduardoamaral.desafiointelbras.data.remote.DeviceApi
import br.com.eduardoamaral.desafiointelbras.data.remote.safeApiCall
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
 * Room e Retrofit (funções suspend) já são main-safe, por isso não há withContext(IO) aqui.
 *
 * O [mutex] serializa as operações que escrevem no banco a partir da API. Sem ele, um refresh
 * iniciado antes de um "criar" poderia terminar depois e apagar o item recém-criado
 * (a lista do servidor ainda não o tinha).
 */
@Singleton
class OfflineFirstDeviceRepository @Inject constructor(
    private val api: DeviceApi,
    private val local: DeviceLocalDataSource,
) : DeviceRepository {

    private val mutex = Mutex()

    override fun observeDevices(query: String, filter: DeviceFilter): Flow<List<Device>> =
        local.observeDevices(query.trim(), filter)

    override fun observeDevice(id: String): Flow<Device?> = local.observeDevice(id)

    /**
     * Busca os dois endpoints em paralelo e sincroniza cada tipo de forma independente.
     * Se um falhar, o outro ainda é atualizado; o erro retornado é o da primeira falha.
     * Em qualquer falha, o banco local daquele tipo fica intacto (offline mostra o último sync).
     */
    override suspend fun refresh(): Outcome<Unit> = mutex.withLock {
        coroutineScope {
            val videos = async { safeApiCall { api.getVideoDevices().data.map { it.toDomain() } } }
            val alarms = async { safeApiCall { api.getAlarmCentrals().data.map { it.toDomain() } } }

            val results = listOf(DeviceType.VIDEO to videos.await(), DeviceType.ALARM to alarms.await())
            results.forEach { (type, result) ->
                if (result is Outcome.Success) local.syncType(type, result.data)
            }
            results.firstNotNullOfOrNull { (_, result) -> result as? Outcome.Failure }
                ?: Outcome.Success(Unit)
        }
    }

    override suspend fun create(input: DeviceInput): Outcome<Device> = mutex.withLock {
        saveRemote {
            when (input) {
                is DeviceInput.Video -> api.createVideoDevice(input.toRequest()).toDomain()
                is DeviceInput.Alarm -> api.createAlarmCentral(input.toRequest()).toDomain()
            }
        }
    }

    override suspend fun update(id: String, input: DeviceInput): Outcome<Device> = mutex.withLock {
        saveRemote {
            when (input) {
                is DeviceInput.Video -> api.updateVideoDevice(id, input.toRequest()).toDomain()
                is DeviceInput.Alarm -> api.updateAlarmCentral(id, input.toRequest()).toDomain()
            }
        }
    }

    override suspend fun delete(id: String): Outcome<Unit> = mutex.withLock {
        // O tipo vem do banco local: ele decide qual endpoint chamar.
        val device = local.getDevice(id) ?: return@withLock Outcome.Failure(AppError.NotFound)
        val result = safeApiCall {
            when (device.type) {
                DeviceType.VIDEO -> api.deleteVideoDevice(id)
                DeviceType.ALARM -> api.deleteAlarmCentral(id)
            }
        }
        when {
            result is Outcome.Success ||
                // Se o servidor já não tem o item, o estado desejado foi alcançado.
                (result is Outcome.Failure && result.error == AppError.NotFound) -> {
                local.delete(id)
                Outcome.Success(Unit)
            }
            else -> result
        }
    }

    override suspend fun toggleFavorite(id: String) = local.toggleFavorite(id)

    /** Chama a API e, com sucesso, grava a resposta do servidor (fonte da verdade) no Room. */
    private suspend fun saveRemote(call: suspend () -> Device): Outcome<Device> =
        when (val result = safeApiCall(call)) {
            is Outcome.Success -> {
                local.upsert(result.data)
                result
            }
            is Outcome.Failure -> result
        }
}
