package br.com.eduardoamaral.desafiointelbras.data.repository

import br.com.eduardoamaral.desafiointelbras.data.local.DeviceLocalDataSource
import br.com.eduardoamaral.desafiointelbras.data.local.OperationType
import br.com.eduardoamaral.desafiointelbras.data.local.PendingOperation
import br.com.eduardoamaral.desafiointelbras.data.local.PendingOperationsLocalDataSource
import br.com.eduardoamaral.desafiointelbras.data.local.TemporaryIds
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
import br.com.eduardoamaral.desafiointelbras.domain.model.WriteResult
import br.com.eduardoamaral.desafiointelbras.domain.model.failureKind
import br.com.eduardoamaral.desafiointelbras.domain.model.toDevice
import br.com.eduardoamaral.desafiointelbras.domain.model.toInput
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
 * passam pela [remote] antes de serem refletidas no banco. Não conhece Ktor, DTOs nem Room.
 *
 * Room e Ktor (funções suspend) já são main-safe, por isso não há withContext(IO) aqui.
 *
 * O [mutex] serializa as operações que escrevem no banco a partir da API (refresh, criar,
 * editar e o envio da fila). Sem ele, um refresh iniciado antes de um "criar" poderia
 * terminar depois e apagar o item recém-criado (a lista do servidor ainda não o tinha).
 *
 * Remoção é otimista (padrão Outbox): muda o banco e enfileira numa transação ([outbox]), e o
 * [syncScheduler] pede o envio, que acontece quando houver conexão.
 *
 * Criar e editar tentam a API primeiro: com rede, um 409 (serial ou MAC repetido) volta na
 * hora e marca o campo no formulário. Sem rede, gravam no banco e entram na mesma fila.
 */
@Singleton
class OfflineFirstDeviceRepository @Inject constructor(
    private val remote: DeviceRemoteDataSource,
    private val local: DeviceLocalDataSource,
    private val outbox: PendingOperationsLocalDataSource,
    private val syncScheduler: SyncScheduler,
) : DeviceRepository {

    private val mutex = Mutex()

    /**
     * Id temporário -> id do servidor, para itens criados offline que já foram enviados.
     * Uma tela aberta com o id antigo (ex.: a edição) continua funcionando depois da troca.
     */
    private val resolvedIds = mutableMapOf<String, String>()

    // Buffer: o worker não fica esperando a UI consumir o aviso.
    private val _syncEvents = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 8)
    override val syncEvents: Flow<SyncEvent> = _syncEvents.asSharedFlow()

    override fun observePendingCount(): Flow<Int> = outbox.observePendingCount()

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
     * serial/MAC. Se a remoção ainda não tivesse chegado, o servidor responderia 409.
     * Se a fila não andou por falta de rede, nem tenta a API: vai direto para a fila.
     */
    override suspend fun create(input: DeviceInput): Outcome<WriteResult> = mutex.withLock {
        val queue = sendPendingOperations()
        if (queue == PendingSyncResult.BLOCKED) return@withLock Outcome.Failure(AppError.Unauthorized)
        if (queue == PendingSyncResult.DONE) {
            val result = remote.create(input)
            if (result is Outcome.Success) {
                local.upsert(result.data)
                return@withLock Outcome.Success(WriteResult.Synced(result.data))
            }
            val failure = result as Outcome.Failure
            if (failure.error.failureKind() != FailureKind.TRANSIENT) return@withLock failure
        }
        val device = input.toDevice(TemporaryIds.new())
        outbox.enqueueCreate(device)
        syncScheduler.schedule()
        Outcome.Success(WriteResult.Queued(device))
    }

    /**
     * Mesma ideia do [create]. Um item criado offline e ainda não enviado (id temporário)
     * nunca vai direto à API: o servidor não o conhece, então a edição entra na fila e
     * segue junto com a criação.
     */
    override suspend fun update(id: String, input: DeviceInput): Outcome<WriteResult> = mutex.withLock {
        val queue = sendPendingOperations()
        if (queue == PendingSyncResult.BLOCKED) return@withLock Outcome.Failure(AppError.Unauthorized)
        val target = resolvedIds[id] ?: id
        if (queue == PendingSyncResult.DONE && !TemporaryIds.isTemporary(target)) {
            val result = remote.update(target, input)
            if (result is Outcome.Success) {
                local.upsert(result.data)
                return@withLock Outcome.Success(WriteResult.Synced(result.data))
            }
            val failure = result as Outcome.Failure
            if (failure.error.failureKind() != FailureKind.TRANSIENT) return@withLock failure
        }
        val device = input.toDevice(target)
        if (!outbox.enqueueUpdate(device)) return@withLock Outcome.Failure(AppError.NotFound)
        syncScheduler.schedule()
        Outcome.Success(WriteResult.Queued(device))
    }

    /**
     * Remoção otimista: some da lista na hora (com ou sem rede) e entra na fila.
     * Não precisa do mutex: a transação do banco já garante a consistência, e o envio
     * (que conversa com a API) acontece depois, sob o mutex, em [syncPendingOperations].
     */
    override suspend fun delete(id: String): Outcome<Unit> {
        if (!outbox.enqueueDelete(resolvedIds[id] ?: id)) return Outcome.Failure(AppError.NotFound)
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
        for (operation in outbox.pendingOperations()) {
            val result = when (operation.operation) {
                OperationType.CREATE -> sendCreate(operation)
                OperationType.UPDATE -> sendUpdate(operation)
                OperationType.DELETE -> sendDelete(operation)
            }
            if (result != null) return result
        }
        return PendingSyncResult.DONE
    }

    /**
     * Envia a versão atual da linha (não uma cópia guardada na fila): criar e depois editar
     * offline vira um POST só, já com os dados finais.
     * @return null para seguir para a próxima operação, ou o motivo para parar a rodada.
     */
    private suspend fun sendCreate(operation: PendingOperation): PendingSyncResult? {
        val temporaryId = operation.deviceId
        // A linha sumiu: não há o que enviar.
        val device = local.getDevice(temporaryId) ?: return dropMissing(temporaryId)
        val result = remote.create(device.toInput())
        if (result is Outcome.Success) {
            outbox.confirmCreate(temporaryId, result.data)
            resolvedIds[temporaryId] = result.data.id
            return null
        }
        val error = (result as Outcome.Failure).error
        return when (error.failureKind()) {
            FailureKind.TRANSIENT -> {
                outbox.registerFailedAttempt(temporaryId)
                PendingSyncResult.RETRY_LATER
            }
            FailureKind.AUTH -> PendingSyncResult.BLOCKED
            // Recusado (ex.: 409, serial já existe): o item só existia no app e é descartado.
            FailureKind.REJECTED, FailureKind.ALREADY_DONE -> {
                outbox.discardCreate(temporaryId)
                _syncEvents.tryEmit(SyncEvent.CreateRejected(device.name, error))
                null
            }
        }
    }

    /** Tira da fila uma operação cujo dispositivo já não existe no banco. */
    private suspend fun dropMissing(id: String): PendingSyncResult? {
        outbox.discardCreate(id)
        return null
    }

    /** Última escrita vence: a API não tem versão para detectar edição concorrente. */
    private suspend fun sendUpdate(operation: PendingOperation): PendingSyncResult? {
        val id = operation.deviceId
        val device = local.getDevice(id) ?: return dropMissing(id)
        val result = remote.update(id, device.toInput())
        if (result is Outcome.Success) {
            outbox.confirmUpdate(result.data)
            return null
        }
        return when ((result as Outcome.Failure).error.failureKind()) {
            FailureKind.TRANSIENT -> {
                outbox.registerFailedAttempt(id)
                PendingSyncResult.RETRY_LATER
            }
            FailureKind.AUTH -> PendingSyncResult.BLOCKED
            FailureKind.ALREADY_DONE -> { // 404: removido no servidor (em outro aparelho)
                outbox.discardCreate(id) // apaga a linha, o favorito e a operação
                _syncEvents.tryEmit(SyncEvent.RemovedOnServer(device.name))
                null
            }
            FailureKind.REJECTED -> { // sai da fila; o próximo refresh traz a versão do servidor
                outbox.dropPendingOperation(id)
                _syncEvents.tryEmit(SyncEvent.UpdateRejected(device.name))
                null
            }
        }
    }

    /** @return null para seguir para a próxima operação, ou o motivo para parar a rodada. */
    private suspend fun sendDelete(operation: PendingOperation): PendingSyncResult? {
        val id = operation.deviceId
        val result = remote.delete(id, operation.deviceType)
        if (result is Outcome.Success) {
            outbox.confirmDelete(id)
            return null
        }
        return when ((result as Outcome.Failure).error.failureKind()) {
            FailureKind.ALREADY_DONE -> { // 404: o servidor já não tem o item
                outbox.confirmDelete(id)
                null
            }
            FailureKind.REJECTED -> { // recusado de vez: o item volta para a lista
                outbox.rollbackDelete(id)
                local.getDevice(id)?.let { _syncEvents.tryEmit(SyncEvent.DeleteRejected(it.name)) }
                null
            }
            FailureKind.TRANSIENT -> {
                outbox.registerFailedAttempt(id)
                PendingSyncResult.RETRY_LATER
            }
            FailureKind.AUTH -> PendingSyncResult.BLOCKED // nada é descartado
        }
    }

    override suspend fun toggleFavorite(id: String) = local.toggleFavorite(id)
}
