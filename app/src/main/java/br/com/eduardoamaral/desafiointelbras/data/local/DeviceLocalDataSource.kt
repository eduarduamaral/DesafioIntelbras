package br.com.eduardoamaral.desafiointelbras.data.local

import br.com.eduardoamaral.desafiointelbras.data.mapper.toDomain
import br.com.eduardoamaral.desafiointelbras.data.mapper.toEntity
import br.com.eduardoamaral.desafiointelbras.domain.model.Device
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceFilter
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Fonte local. A interface existe para o repositório ser testado com um fake
 * em teste unitário puro (sem Android/Room).
 */
interface DeviceLocalDataSource {
    fun observeDevices(query: String, filter: DeviceFilter): Flow<List<Device>>
    fun observeDevice(id: String): Flow<Device?>

    /** Total cadastrado, sem filtro nem busca. Contado no próprio banco. */
    fun observeCount(): Flow<Int>

    suspend fun getDevice(id: String): Device?

    /** Substitui os dispositivos de um tipo pelos do servidor, sem mexer nos favoritos. */
    suspend fun syncType(type: DeviceType, devices: List<Device>)
    suspend fun upsert(device: Device)
    suspend fun delete(id: String)
    suspend fun toggleFavorite(id: String)
}

class RoomDeviceLocalDataSource @Inject constructor(
    private val dao: DeviceDao,
) : DeviceLocalDataSource {

    override fun observeDevices(query: String, filter: DeviceFilter): Flow<List<Device>> {
        val type = when (filter) {
            DeviceFilter.VIDEO -> DeviceType.VIDEO.name
            DeviceFilter.ALARM -> DeviceType.ALARM.name
            DeviceFilter.ALL, DeviceFilter.FAVORITES -> null
        }
        return dao.observeDevices(
            query = query.escapeLike(),
            type = type,
            onlyFavorites = filter == DeviceFilter.FAVORITES,
        ).map { rows -> rows.mapNotNull { it.toDomain() } }
    }

    override fun observeDevice(id: String): Flow<Device?> =
        dao.observeDevice(id).map { it?.toDomain() }

    override fun observeCount(): Flow<Int> = dao.observeCount()

    override suspend fun getDevice(id: String): Device? = dao.getDevice(id)?.toDomain()

    override suspend fun syncType(type: DeviceType, devices: List<Device>) =
        dao.syncType(type.name, devices.map { it.toEntity() })

    override suspend fun upsert(device: Device) = dao.upsert(device.toEntity())

    override suspend fun delete(id: String) = dao.deleteById(id)

    override suspend fun toggleFavorite(id: String) = dao.toggleFavorite(id)

    /** Evita que "%" ou "_" digitados na busca virem curingas do LIKE. */
    private fun String.escapeLike(): String =
        replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
