package br.com.eduardoamaral.desafiointelbras.testutil

import br.com.eduardoamaral.desafiointelbras.data.local.DeviceLocalDataSource
import br.com.eduardoamaral.desafiointelbras.data.remote.DeviceApi
import br.com.eduardoamaral.desafiointelbras.data.remote.dto.AlarmCentralDto
import br.com.eduardoamaral.desafiointelbras.data.remote.dto.AlarmCentralRequest
import br.com.eduardoamaral.desafiointelbras.data.remote.dto.ListResponse
import br.com.eduardoamaral.desafiointelbras.data.remote.dto.VideoDeviceDto
import br.com.eduardoamaral.desafiointelbras.data.remote.dto.VideoDeviceRequest
import br.com.eduardoamaral.desafiointelbras.domain.model.AppError
import br.com.eduardoamaral.desafiointelbras.domain.model.Device
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceDetails
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceFilter
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceInput
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceType
import br.com.eduardoamaral.desafiointelbras.domain.model.Outcome
import br.com.eduardoamaral.desafiointelbras.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.IOException

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

/** Filtro em memória com a mesma regra da query SQL. */
private fun List<Device>.applyFilter(query: String, filter: DeviceFilter): List<Device> =
    filter { it.name.contains(query, ignoreCase = true) }
        .filter {
            when (filter) {
                DeviceFilter.ALL -> true
                DeviceFilter.FAVORITES -> it.isFavorite
                DeviceFilter.VIDEO -> it.type == DeviceType.VIDEO
                DeviceFilter.ALARM -> it.type == DeviceType.ALARM
            }
        }
        .sortedBy { it.name.lowercase() }

class FakeDeviceRepository : DeviceRepository {
    val devices = MutableStateFlow<List<Device>>(emptyList())
    var refreshResult: Outcome<Unit> = Outcome.Success(Unit)
    var writeError: AppError? = null
    var refreshCount = 0
    val created = mutableListOf<DeviceInput>()
    val updated = mutableListOf<Pair<String, DeviceInput>>()

    override fun observeDevices(query: String, filter: DeviceFilter): Flow<List<Device>> =
        devices.map { it.applyFilter(query, filter) }

    override fun observeDevice(id: String): Flow<Device?> =
        devices.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun refresh(): Outcome<Unit> {
        refreshCount++
        return refreshResult
    }

    override suspend fun create(input: DeviceInput): Outcome<Device> {
        created += input
        return result("new", input)
    }

    override suspend fun update(id: String, input: DeviceInput): Outcome<Device> {
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

    private fun result(id: String, input: DeviceInput): Outcome<Device> {
        writeError?.let { return Outcome.Failure(it) }
        val details = when (input) {
            is DeviceInput.Video -> DeviceDetails.Video(input.serial, input.username, input.password)
            is DeviceInput.Alarm -> DeviceDetails.Alarm(input.macAddress, input.password)
        }
        return Outcome.Success(Device(id, input.name, details))
    }
}

class FakeDeviceLocalDataSource : DeviceLocalDataSource {
    private val devices = MutableStateFlow<List<Device>>(emptyList())
    private val favorites = MutableStateFlow<Set<String>>(emptySet())

    val snapshot: List<Device> get() = devices.value.withFavorites(favorites.value)

    override fun observeDevices(query: String, filter: DeviceFilter): Flow<List<Device>> =
        combine(devices, favorites) { list, favs -> list.withFavorites(favs).applyFilter(query, filter) }

    override fun observeDevice(id: String): Flow<Device?> =
        combine(devices, favorites) { list, favs -> list.withFavorites(favs).firstOrNull { it.id == id } }

    override suspend fun getDevice(id: String): Device? = snapshot.firstOrNull { it.id == id }

    override suspend fun syncType(type: DeviceType, devices: List<Device>) {
        this.devices.update { current -> current.filterNot { it.type == type } + devices }
        // espelha o CASCADE: favorito de item que sumiu é removido
        val ids = this.devices.value.map { it.id }.toSet()
        favorites.update { favs -> favs.filterTo(mutableSetOf()) { it in ids } }
    }

    override suspend fun upsert(device: Device) {
        devices.update { list -> list.filterNot { it.id == device.id } + device }
    }

    override suspend fun delete(id: String) {
        devices.update { list -> list.filterNot { it.id == id } }
        favorites.update { it - id }
    }

    override suspend fun toggleFavorite(id: String) {
        favorites.update { if (id in it) it - id else it + id }
    }

    private fun List<Device>.withFavorites(favs: Set<String>) =
        map { it.copy(isFavorite = it.id in favs) }
}

/** Simula o servidor em memória. [videoError]/[alarmError] fazem o respectivo endpoint falhar. */
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
        return VideoDeviceDto("v${nextId++}", body.name, body.serial, body.username, body.password)
            .also { videos += it }
    }

    override suspend fun updateVideoDevice(id: String, body: VideoDeviceRequest): VideoDeviceDto {
        videoError?.let { throw it }
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
        return AlarmCentralDto("a${nextId++}", body.name, body.macAddress, body.password)
            .also { alarms += it }
    }

    override suspend fun updateAlarmCentral(id: String, body: AlarmCentralRequest): AlarmCentralDto {
        alarmError?.let { throw it }
        val updated = AlarmCentralDto(id, body.name, body.macAddress, body.password)
        alarms.replaceAll { if (it.id == id) updated else it }
        return updated
    }

    override suspend fun deleteAlarmCentral(id: String) {
        alarmError?.let { throw it }
        deletedAlarmIds += id
        alarms.removeAll { it.id == id }
    }

    companion object {
        val NETWORK_ERROR = IOException("offline")
    }
}
