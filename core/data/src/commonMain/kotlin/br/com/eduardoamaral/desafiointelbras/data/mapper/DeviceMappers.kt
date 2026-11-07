package br.com.eduardoamaral.desafiointelbras.data.mapper

import br.com.eduardoamaral.desafiointelbras.data.local.DeviceEntity
import br.com.eduardoamaral.desafiointelbras.data.local.DeviceWithFavorite
import br.com.eduardoamaral.desafiointelbras.data.local.SyncState
import br.com.eduardoamaral.desafiointelbras.data.remote.dto.AlarmCentralDto
import br.com.eduardoamaral.desafiointelbras.data.remote.dto.AlarmCentralRequest
import br.com.eduardoamaral.desafiointelbras.data.remote.dto.VideoDeviceDto
import br.com.eduardoamaral.desafiointelbras.data.remote.dto.VideoDeviceRequest
import br.com.eduardoamaral.desafiointelbras.domain.model.Device
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceDetails
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceInput
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceType
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceValidator

// ---- API -> domínio ----

fun VideoDeviceDto.toDomain() = Device(
    id = id,
    name = name,
    details = DeviceDetails.Video(serial = serial, username = username, password = password),
)

fun AlarmCentralDto.toDomain() = Device(
    id = id,
    name = name,
    details = DeviceDetails.Alarm(macAddress = macAddress, password = password),
)

// ---- domínio -> API ----

fun DeviceInput.Video.toRequest() = VideoDeviceRequest(
    name = name.trim(),
    serial = serial.trim(),
    username = username.trim(),
    password = password,
)

fun DeviceInput.Alarm.toRequest() = AlarmCentralRequest(
    name = name.trim(),
    macAddress = DeviceValidator.normalizeMac(macAddress),
    password = password,
)

// ---- Room <-> domínio ----

/** isFavorite não vai para esta tabela: ele vive em favorites. */
fun Device.toEntity(): DeviceEntity = when (val d = details) {
    is DeviceDetails.Video -> DeviceEntity(
        id = id,
        type = DeviceType.VIDEO.name,
        name = name,
        password = d.password,
        serial = d.serial,
        username = d.username,
    )
    is DeviceDetails.Alarm -> DeviceEntity(
        id = id,
        type = DeviceType.ALARM.name,
        name = name,
        password = d.password,
        macAddress = d.macAddress,
    )
}

/** Retorna null se a linha estiver inconsistente (tipo desconhecido ou coluna obrigatória nula). */
fun DeviceWithFavorite.toDomain(): Device? {
    val details = device.toDetails() ?: return null
    return Device(
        id = device.id,
        name = device.name,
        details = details,
        isFavorite = isFavorite,
        // A view já esconde remoções pendentes; o que sobra pendente é criação ou edição.
        isPendingSync = device.syncState != SyncState.SYNCED.name,
    )
}

private fun DeviceEntity.toDetails(): DeviceDetails? = when (type) {
    DeviceType.VIDEO.name ->
        if (serial != null && username != null) {
            DeviceDetails.Video(serial = serial, username = username, password = password)
        } else {
            null
        }
    DeviceType.ALARM.name -> macAddress?.let { DeviceDetails.Alarm(macAddress = it, password = password) }
    else -> null
}
