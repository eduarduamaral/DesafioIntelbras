package br.com.eduardoamaral.desafiointelbras.data.local

import br.com.eduardoamaral.desafiointelbras.data.mapper.toEntity
import br.com.eduardoamaral.desafiointelbras.domain.model.Device
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceType
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Fila de operações pendentes (padrão Outbox), separada de [DeviceLocalDataSource]:
 * quem só lê e grava dispositivos não precisa conhecer a fila, e cada interface fica
 * pequena (princípio da segregação de interfaces). As duas usam o mesmo banco e as
 * mesmas transações do [DeviceDao].
 */
interface PendingOperationsLocalDataSource {
    /** Esconde o dispositivo e põe a remoção na fila, numa transação. false se não existir. */
    suspend fun enqueueDelete(id: String): Boolean
    suspend fun pendingOperations(): List<PendingOperation>
    fun observePendingCount(): Flow<Int>
    suspend fun confirmDelete(id: String)
    suspend fun rollbackDelete(id: String)
    suspend fun registerFailedAttempt(id: String)

    /** Grava um item criado offline (id temporário) e põe a criação na fila. */
    suspend fun enqueueCreate(device: Device)

    /** Grava uma edição offline e põe na fila. false se não existe ou está sendo removido. */
    suspend fun enqueueUpdate(device: Device): Boolean

    /** Troca o id temporário pelo do servidor, levando favorito e posição na lista. */
    suspend fun confirmCreate(temporaryId: String, created: Device)
    suspend fun confirmUpdate(updated: Device)

    /** O servidor recusou a criação: o item, que só existia no app, é descartado. */
    suspend fun discardCreate(temporaryId: String)

    /** Tira a operação da fila e volta a aceitar a versão do servidor no próximo refresh. */
    suspend fun dropPendingOperation(id: String)
}

/** Operação na fila, já com tipos do app (sem strings do banco). */
data class PendingOperation(
    val deviceId: String,
    val operation: OperationType,
    val deviceType: DeviceType,
)

/**
 * Ids de itens criados offline. O prefixo os diferencia dos UUIDs do servidor
 * (útil em log e em testes); o app nunca envia esse id para a API.
 */
object TemporaryIds {
    private const val PREFIX = "local-"

    // kotlin.uuid (e não java.util.UUID): o mesmo código roda no iOS. Mesmo formato de texto.
    @OptIn(ExperimentalUuidApi::class)
    fun new(): String = PREFIX + Uuid.random()

    fun isTemporary(id: String): Boolean = id.startsWith(PREFIX)
}

/**
 * [now] dá o horário que ordena a fila (milissegundos desde 1970). Parâmetro para os
 * testes poderem fixar o tempo; o padrão é o relógio do sistema, em qualquer plataforma.
 */
class RoomPendingOperationsLocalDataSource(
    private val dao: DeviceDao,
    private val now: () -> Long = ::systemTimeMillis,
) : PendingOperationsLocalDataSource {

    override suspend fun enqueueDelete(id: String): Boolean =
        dao.enqueueDelete(id, createdAt = now())

    override suspend fun pendingOperations(): List<PendingOperation> =
        dao.getPendingOperations().mapNotNull { entity ->
            PendingOperation(
                deviceId = entity.deviceId,
                operation = OperationType.valueOf(entity.operation),
                deviceType = DeviceType.entries.firstOrNull { it.name == entity.deviceType }
                    ?: return@mapNotNull null,
            )
        }

    override fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    override suspend fun confirmDelete(id: String) = dao.confirmDelete(id)

    override suspend fun rollbackDelete(id: String) = dao.rollbackDelete(id)

    override suspend fun registerFailedAttempt(id: String) = dao.incrementAttempts(id)

    override suspend fun enqueueCreate(device: Device) =
        dao.enqueueCreate(device.toEntity(), createdAt = now())

    override suspend fun enqueueUpdate(device: Device): Boolean =
        dao.enqueueUpdate(device.toEntity(), createdAt = now())

    override suspend fun confirmCreate(temporaryId: String, created: Device) =
        dao.confirmCreate(temporaryId, created.toEntity())

    override suspend fun confirmUpdate(updated: Device) = dao.confirmUpdate(updated.toEntity())

    // Mesmo efeito de confirmar uma remoção: apaga a linha, o favorito e a operação.
    override suspend fun discardCreate(temporaryId: String) = dao.confirmDelete(temporaryId)

    // Mesmo efeito de desfazer uma remoção: estado SYNCED e fila limpa.
    override suspend fun dropPendingOperation(id: String) = dao.rollbackDelete(id)
}

// kotlin.time.Clock (e não System.currentTimeMillis): existe também no iOS.
@OptIn(ExperimentalTime::class)
private fun systemTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
