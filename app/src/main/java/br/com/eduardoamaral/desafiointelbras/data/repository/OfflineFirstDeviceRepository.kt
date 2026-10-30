package br.com.eduardoamaral.desafiointelbras.data.repository

import br.com.eduardoamaral.desafiointelbras.data.local.DeviceLocalDataSource
import br.com.eduardoamaral.desafiointelbras.data.local.OperationType
import br.com.eduardoamaral.desafiointelbras.data.local.PendingOperation
import br.com.eduardoamaral.desafiointelbras.data.remote.DeviceRemoteDataSource
import br.com.eduardoamaral.desafiointelbras.data.sync.SyncScheduler
import br.com.eduardoamaral.desafiointelbras.domain.model.AppError
import br.com.eduardoamaral.desafiointelbras.domain.model.Device
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceFilter
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceInput
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceType
import br.com.eduardoamaral.desafiointelbras.domain.model.FailureKind
import br.com.eduardoamaral.desafiointelbras.domain.model.Outcome
import br.com.eduardoamaral.desafiointelbras.domain.model.PendingSyncResult
import br.com.eduardoamaral.desafiointelbras.domain.model.SyncEvent
import br.com.eduardoamaral.desafiointelbras.domain.model.failureKind
import br.com.eduardoamaral.desafiointelbras.domain.repository.DeviceRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
 * O [mutex] serializa as operações que escrevem no banco a partir da API (refresh, criar,
 * editar e o envio da fila). Sem ele, um refresh iniciado antes de um "criar" poderia
 * terminar depois e apagar o item recém-criado (a lista do servidor ainda não o tinha).
 *
 * Remoção é otimista (padrão Outbox): muda o banco e enfileira numa transação, e o
 * [syncScheduler] pede o envio, que acontece quando houver conexão.
 */
@Singleton
class OfflineFirstDeviceRepository @Inject constructor(
    private val remote: DeviceRemoteDataSource,
    private val local: DeviceLocalDataSource,
    private val syncScheduler: SyncScheduler,
) : DeviceRepository {

    private val mutex = Mutex()

    // Buffer: o worker não fica esperando a UI consumir o aviso.
    private val _syncEvents = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 8)
    override val syncEvents: Flow<SyncEvent> = _syncEvents.asSharedFlow()

    override fun observePendingCount(): Flow<Int> = local.observePendingCount()

    override fun observeDevices(query: String, filter: DeviceFilter): Flow<List<Device>> =
        local.observeDevices(query.trim(), filter)

    override fun observeDevice(id: String): Flow<Device?> = local.observeDevice(id)

    override fun observeDeviceCount(): Flow<Int> = local.observeCount()

    /**
     * Busca os dois tipos em paralelo e sincroniza cada um de forma independente.
     * Se um falhar, o outro ainda é atualizado; o erro retornado é o da primeira falha.
     * Em qualquer falha, o banco local daquele tipo fica intacto (offline mostra o último sync).
     *
     * Antes de buscar, envia a fila de pendências ("empurra, depois puxa"): assim o que o
     * usuário mudou offline chega ao servidor antes de a lista do servidor ser aplicada.
     */
    override suspend fun refresh(): Outcome<Unit> = mutex.withLock {
        sendPendingOperations()
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

    /**
     * Antes de criar, envia a fila. Caso real: remover offline e recriar com o mesmo
     * serial/MAC. Se a remoção ainda não tivesse chegado, o servidor responderia 409
     * (serial/MAC já existe). Sem rede, o envio falha rápido e a criação falha igual.
     */
    override suspend fun create(input: DeviceInput): Outcome<Device> = mutex.withLock {
        sendPendingOperations()
        remote.create(input).alsoSaveLocally()
    }

    override suspend fun update(id: String, input: DeviceInput): Outcome<Device> = mutex.withLock {
        remote.update(id, input).alsoSaveLocally()
    }

    /**
     * Remoção otimista: some da lista na hora (com ou sem rede) e entra na fila.
     * Não precisa do mutex: a transação do banco já garante a consistência, e o envio
     * (que conversa com a API) acontece depois, sob o mutex, em [syncPendingOperations].
     */
    override suspend fun delete(id: String): Outcome<Unit> {
        if (!local.enqueueDelete(id)) return Outcome.Failure(AppError.NotFound)
        syncScheduler.schedule()
        return Outcome.Success(Unit)
    }

    override suspend fun syncPendingOperations(): PendingSyncResult = mutex.withLock {
        sendPendingOperations()
    }

    /**
     * Envia a fila em ordem (da mais antiga para a mais nova). Chamar só com o [mutex].
     *
     * Para na primeira falha temporária, em vez de pular para a próxima operação: manter a
     * ordem importa quando houver criar/editar/remover do mesmo item, e sem rede as próximas
     * falhariam do mesmo jeito.
     */
    private suspend fun sendPendingOperations(): PendingSyncResult {
        for (operation in local.pendingOperations()) {
            val result = when (operation.operation) {
                OperationType.DELETE -> sendDelete(operation)
                // Criar/editar offline ainda não geram operações (ver README, próximos passos).
                OperationType.CREATE, OperationType.UPDATE -> null
            }
            if (result != null) return result
        }
        return PendingSyncResult.DONE
    }

    /** @return null para seguir para a próxima operação, ou o motivo para parar a rodada. */
    private suspend fun sendDelete(operation: PendingOperation): PendingSyncResult? {
        val id = operation.deviceId
        val result = remote.delete(id, operation.deviceType)
        if (result is Outcome.Success) {
            local.confirmDelete(id)
            return null
        }
        return when ((result as Outcome.Failure).error.failureKind()) {
            FailureKind.ALREADY_DONE -> { // 404: o servidor já não tem o item
                local.confirmDelete(id)
                null
            }
            FailureKind.REJECTED -> { // recusado de vez: o item volta para a lista
                local.rollbackDelete(id)
                local.getDevice(id)?.let { _syncEvents.tryEmit(SyncEvent.DeleteRejected(it.name)) }
                null
            }
            FailureKind.TRANSIENT -> {
                local.registerFailedAttempt(id)
                PendingSyncResult.RETRY_LATER
            }
            FailureKind.AUTH -> PendingSyncResult.BLOCKED // nada é descartado
        }
    }

    override suspend fun toggleFavorite(id: String) = local.toggleFavorite(id)

    /** Com sucesso, grava a resposta do servidor (que é a versão oficial) no banco local. */
    private suspend fun Outcome<Device>.alsoSaveLocally(): Outcome<Device> {
        if (this is Outcome.Success) local.upsert(data)
        return this
    }
}
