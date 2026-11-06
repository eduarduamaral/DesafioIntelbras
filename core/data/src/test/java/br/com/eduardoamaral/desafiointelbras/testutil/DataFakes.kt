package br.com.eduardoamaral.desafiointelbras.testutil

import br.com.eduardoamaral.desafiointelbras.data.local.DeviceLocalDataSource
import br.com.eduardoamaral.desafiointelbras.data.local.OperationType
import br.com.eduardoamaral.desafiointelbras.data.local.PendingOperation
import br.com.eduardoamaral.desafiointelbras.data.local.PendingOperationsLocalDataSource
import br.com.eduardoamaral.desafiointelbras.data.remote.DeviceApi
import br.com.eduardoamaral.desafiointelbras.data.remote.HttpStatusException
import br.com.eduardoamaral.desafiointelbras.data.remote.dto.AlarmCentralDto
import br.com.eduardoamaral.desafiointelbras.data.remote.dto.AlarmCentralRequest
import br.com.eduardoamaral.desafiointelbras.data.remote.dto.ListResponse
import br.com.eduardoamaral.desafiointelbras.data.remote.dto.VideoDeviceDto
import br.com.eduardoamaral.desafiointelbras.data.remote.dto.VideoDeviceRequest
import br.com.eduardoamaral.desafiointelbras.data.sync.SyncScheduler
import br.com.eduardoamaral.desafiointelbras.domain.model.Device
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceFilter
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.io.IOException

/**
 * Banco local em memória, com as mesmas regras do DeviceDao (testadas com SQLite real em
 * DeviceDaoTest): remoção pendente esconde o item sem apagar, a sincronização não
 * sobrescreve itens com operação na fila e a lista vem do mais recente para o mais antigo.
 *
 * Implementa as duas interfaces sobre o mesmo estado em memória, como o Room faz com um banco só.
 */
class FakeDeviceLocalDataSource : DeviceLocalDataSource, PendingOperationsLocalDataSource {
    private val devices = MutableStateFlow<List<Device>>(emptyList())
    private val favorites = MutableStateFlow<Set<String>>(emptySet())

    /** LinkedHashMap: mantém a ordem de entrada, como o ORDER BY created_at do banco. */
    private val pending = MutableStateFlow<Map<String, PendingOperation>>(linkedMapOf())
    val failedAttempts = mutableMapOf<String, Int>()

    /** Espelha devices.sort_order: número atribuído uma vez, na primeira gravação. */
    private val sortOrder = mutableMapOf<String, Long>()
    private fun assignOrder(id: String) {
        sortOrder.getOrPut(id) { (sortOrder.values.maxOrNull() ?: 0L) + 1 }
    }
    private fun List<Device>.newestFirst(): List<Device> =
        sortedWith(compareByDescending<Device> { sortOrder[it.id] ?: 0L }.thenBy { it.id })

    /** O que a lista mostra, na mesma ordem: sem remoções pendentes, mais recente primeiro. */
    val snapshot: List<Device>
        get() = visible(devices.value, pending.value).withState(favorites.value, pending.value).newestFirst()
    val pendingIds: Set<String> get() = pending.value.keys
    fun hasRow(id: String): Boolean = devices.value.any { it.id == id }

    override fun observeDevices(query: String, filter: DeviceFilter): Flow<List<Device>> =
        combine(devices, favorites, pending) { list, favs, pend ->
            visible(list, pend).withState(favs, pend).newestFirst().applyFilter(query, filter)
        }

    override fun observeDevice(id: String): Flow<Device?> =
        combine(devices, favorites, pending) { list, favs, pend ->
            visible(list, pend).withState(favs, pend).firstOrNull { it.id == id }
        }

    override fun observeCount(): Flow<Int> =
        combine(devices, pending) { list, pend -> visible(list, pend).size }

    override suspend fun getDevice(id: String): Device? = snapshot.firstOrNull { it.id == id }

    override suspend fun syncType(type: DeviceType, devices: List<Device>) {
        val pendingIds = pending.value.keys
        devices.filterNot { it.id in pendingIds }.forEach { assignOrder(it.id) }
        this.devices.update { current ->
            // Itens com operação na fila mantêm a versão local (não "ressuscitam").
            val localPending = current.filter { it.id in pendingIds }.associateBy { it.id }
            val serverIds = devices.map { it.id }.toSet()
            // Itens com operação na fila que o servidor não conhece (criados offline) ficam.
            val localOnly = current.filter { it.type == type && it.id in pendingIds && it.id !in serverIds }
            current.filterNot { it.type == type } + devices.map { localPending[it.id] ?: it } + localOnly
        }
        // espelha o CASCADE: favorito de item que sumiu é removido
        val ids = this.devices.value.map { it.id }.toSet()
        favorites.update { favs -> favs.filterTo(mutableSetOf()) { it in ids } }
    }

    override suspend fun upsert(device: Device) {
        assignOrder(device.id)
        devices.update { list -> list.filterNot { it.id == device.id } + device }
    }

    override suspend fun toggleFavorite(id: String) {
        favorites.update { if (id in it) it - id else it + id }
    }

    override suspend fun enqueueDelete(id: String): Boolean {
        val device = devices.value.firstOrNull { it.id == id } ?: return false
        if (id !in pending.value) {
            pending.update { it + (id to PendingOperation(id, OperationType.DELETE, device.type)) }
        }
        return true
    }

    override suspend fun pendingOperations(): List<PendingOperation> = pending.value.values.toList()

    override fun observePendingCount(): Flow<Int> = pending.map { it.size }

    override suspend fun confirmDelete(id: String) {
        devices.update { list -> list.filterNot { it.id == id } }
        favorites.update { it - id }
        pending.update { it - id }
    }

    override suspend fun rollbackDelete(id: String) {
        pending.update { it - id }
    }

    override suspend fun registerFailedAttempt(id: String) {
        failedAttempts[id] = (failedAttempts[id] ?: 0) + 1
    }

    override suspend fun enqueueCreate(device: Device) {
        assignOrder(device.id)
        devices.update { list -> list.filterNot { it.id == device.id } + device }
        pending.update { it + (device.id to PendingOperation(device.id, OperationType.CREATE, device.type)) }
    }

    override suspend fun enqueueUpdate(device: Device): Boolean {
        if (devices.value.none { it.id == device.id }) return false
        val previous = pending.value[device.id]
        if (previous?.operation == OperationType.DELETE) return false
        devices.update { list -> list.map { if (it.id == device.id) device else it } }
        if (previous == null) {
            pending.update { it + (device.id to PendingOperation(device.id, OperationType.UPDATE, device.type)) }
        }
        return true
    }

    override suspend fun confirmCreate(temporaryId: String, created: Device) {
        sortOrder[temporaryId]?.let { sortOrder[created.id] = it } ?: assignOrder(created.id)
        devices.update { list -> list.filterNot { it.id == temporaryId } + created.copy(isFavorite = false) }
        if (temporaryId in favorites.value) favorites.update { it - temporaryId + created.id }
        pending.update { it - temporaryId }
    }

    override suspend fun confirmUpdate(updated: Device) {
        devices.update { list -> list.map { if (it.id == updated.id) updated else it } }
        pending.update { it - updated.id }
    }

    override suspend fun discardCreate(temporaryId: String) = confirmDelete(temporaryId)

    override suspend fun dropPendingOperation(id: String) = rollbackDelete(id)

    /** Igual à view do Room: só a remoção pendente esconde o item; criação e edição aparecem. */
    private fun visible(list: List<Device>, pend: Map<String, PendingOperation>) =
        list.filterNot { pend[it.id]?.operation == OperationType.DELETE }

    /** Favorito e "aguardando envio" calculados na leitura, como a view e o mapper fazem. */
    private fun List<Device>.withState(favs: Set<String>, pend: Map<String, PendingOperation>) =
        map { it.copy(isFavorite = it.id in favs, isPendingSync = it.id in pend) }
}

/** Conta os pedidos de envio da fila, no lugar do WorkManager. */
class FakeSyncScheduler : SyncScheduler {
    var scheduled = 0
    override fun schedule() {
        scheduled++
    }
}

/**
 * Simula o servidor em memória. [videoError]/[alarmError] fazem o respectivo endpoint falhar.
 * Como a API real, recusa com 409 serial (vídeo) ou MAC (alarme) já cadastrado.
 */
class FakeDeviceApi : DeviceApi {
    val videos = mutableListOf<VideoDeviceDto>()
    val alarms = mutableListOf<AlarmCentralDto>()
    var videoError: Throwable? = null
    var alarmError: Throwable? = null
    val deletedVideoIds = mutableListOf<String>()
    val deletedAlarmIds = mutableListOf<String>()
    private var nextId = 1

    override suspend fun getVideoDevices(): ListResponse<VideoDeviceDto> {
        videoError?.let { throw it }
        return ListResponse(videos.size, videos.toList())
    }

    override suspend fun createVideoDevice(body: VideoDeviceRequest): VideoDeviceDto {
        videoError?.let { throw it }
        if (videos.any { it.serial == body.serial }) throw conflict()
        return VideoDeviceDto("v${nextId++}", body.name, body.serial, body.username, body.password)
            .also { videos += it }
    }

    override suspend fun updateVideoDevice(id: String, body: VideoDeviceRequest): VideoDeviceDto {
        videoError?.let { throw it }
        if (videos.none { it.id == id }) throw httpError(404)
        val updated = VideoDeviceDto(id, body.name, body.serial, body.username, body.password)
        videos.replaceAll { if (it.id == id) updated else it }
        return updated
    }

    override suspend fun deleteVideoDevice(id: String) {
        videoError?.let { throw it }
        deletedVideoIds += id
        videos.removeAll { it.id == id }
    }

    override suspend fun getAlarmCentrals(): ListResponse<AlarmCentralDto> {
        alarmError?.let { throw it }
        return ListResponse(alarms.size, alarms.toList())
    }

    override suspend fun createAlarmCentral(body: AlarmCentralRequest): AlarmCentralDto {
        alarmError?.let { throw it }
        if (alarms.any { it.macAddress == body.macAddress }) throw conflict()
        return AlarmCentralDto("a${nextId++}", body.name, body.macAddress, body.password)
            .also { alarms += it }
    }

    override suspend fun updateAlarmCentral(id: String, body: AlarmCentralRequest): AlarmCentralDto {
        alarmError?.let { throw it }
        if (alarms.none { it.id == id }) throw httpError(404)
        val updated = AlarmCentralDto(id, body.name, body.macAddress, body.password)
        alarms.replaceAll { if (it.id == id) updated else it }
        return updated
    }

    override suspend fun deleteAlarmCentral(id: String) {
        alarmError?.let { throw it }
        deletedAlarmIds += id
        alarms.removeAll { it.id == id }
    }

    private fun conflict() = httpError(409)

    private fun httpError(code: Int) = HttpStatusException(code)

    companion object {
        val NETWORK_ERROR = IOException("offline")
    }
}
