package br.com.eduardoamaral.desafiointelbras.testutil

import br.com.eduardoamaral.desafiointelbras.domain.model.AppError
import br.com.eduardoamaral.desafiointelbras.domain.model.Device
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceDetails
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceFilter
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceInput
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceType
import br.com.eduardoamaral.desafiointelbras.domain.model.Outcome
import br.com.eduardoamaral.desafiointelbras.domain.model.PendingSyncResult
import br.com.eduardoamaral.desafiointelbras.domain.model.SyncEvent
import br.com.eduardoamaral.desafiointelbras.domain.model.WriteResult
import br.com.eduardoamaral.desafiointelbras.domain.model.toDevice
import br.com.eduardoamaral.desafiointelbras.domain.network.NetworkMonitor
import br.com.eduardoamaral.desafiointelbras.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

// ---- Fábricas de dados de teste ----

fun videoDevice(id: String, name: String, isFavorite: Boolean = false) = Device(
    id = id,
    name = name,
    details = DeviceDetails.Video(serial = "SN$id", username = "admin", password = "1234"),
    isFavorite = isFavorite,
)

fun alarmDevice(id: String, name: String, isFavorite: Boolean = false) = Device(
    id = id,
    name = name,
    details = DeviceDetails.Alarm(macAddress = "1A2B3C4D5E6F", password = "1234"),
    isFavorite = isFavorite,
)

val networkFailure = Outcome.Failure(AppError.Network)

/** Filtro em memória com a mesma regra da query SQL. Não reordena: recebe a lista já na ordem final. */
fun List<Device>.applyFilter(query: String, filter: DeviceFilter): List<Device> =
    filter { it.name.contains(query, ignoreCase = true) }
        .filter {
            when (filter) {
                DeviceFilter.ALL -> true
                DeviceFilter.FAVORITES -> it.isFavorite
                DeviceFilter.VIDEO -> it.type == DeviceType.VIDEO
                DeviceFilter.ALARM -> it.type == DeviceType.ALARM
            }
        }

class FakeDeviceRepository : DeviceRepository {
    /** Na ordem em que foram adicionados; a leitura devolve do mais recente para o mais antigo. */
    val devices = MutableStateFlow<List<Device>>(emptyList())
    var refreshResult: Outcome<Unit> = Outcome.Success(Unit)
    var writeError: AppError? = null

    /** true simula "sem rede": criar e editar dão certo, mas ficam na fila. */
    var queueWrites = false
    var refreshCount = 0
    val created = mutableListOf<DeviceInput>()
    val updated = mutableListOf<Pair<String, DeviceInput>>()

    override fun observeDevices(query: String, filter: DeviceFilter): Flow<List<Device>> =
        devices.map { it.asReversed().applyFilter(query, filter) }

    override fun observeDevice(id: String): Flow<Device?> =
        devices.map { list -> list.firstOrNull { it.id == id } }

    override fun observeDeviceCount(): Flow<Int> = devices.map { it.size }

    override suspend fun refresh(): Outcome<Unit> {
        refreshCount++
        return refreshResult
    }

    override suspend fun create(input: DeviceInput): Outcome<WriteResult> {
        created += input
        return result("new", input)
    }

    override suspend fun update(id: String, input: DeviceInput): Outcome<WriteResult> {
        updated += id to input
        return result(id, input)
    }

    override suspend fun delete(id: String): Outcome<Unit> {
        writeError?.let { return Outcome.Failure(it) }
        devices.update { list -> list.filterNot { it.id == id } }
        return Outcome.Success(Unit)
    }

    override suspend fun toggleFavorite(id: String) {
        devices.update { list ->
            list.map { if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it }
        }
    }

    var pendingSyncResult = PendingSyncResult.DONE
    override suspend fun syncPendingOperations(): PendingSyncResult = pendingSyncResult

    val pendingCount = MutableStateFlow(0)
    override fun observePendingCount(): Flow<Int> = pendingCount

    val syncEventsFlow = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 8)
    override val syncEvents: Flow<SyncEvent> = syncEventsFlow

    private fun result(id: String, input: DeviceInput): Outcome<WriteResult> {
        writeError?.let { return Outcome.Failure(it) }
        val device = input.toDevice(id)
        return Outcome.Success(if (queueWrites) WriteResult.Queued(device) else WriteResult.Synced(device))
    }
}

/** Estado da conexão controlado pelo teste. */
class FakeNetworkMonitor(initiallyOnline: Boolean = true) : NetworkMonitor {
    val online = MutableStateFlow(initiallyOnline)
    override val isOnline: Flow<Boolean> = online
}
