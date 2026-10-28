package br.com.eduardoamaral.desafiointelbras.data.remote.dto

import kotlinx.serialization.Serializable

/** As listagens vêm embrulhadas: { "count": 1, "data": [...] }. */
@Serializable
data class ListResponse<T>(
    val count: Int = 0,
    val data: List<T> = emptyList(),
)

// ---- /video-devices ----

@Serializable
data class VideoDeviceDto(
    val id: String,
    val name: String,
    val serial: String,
    val username: String,
    val password: String,
)

/** Usado no POST e no PATCH (a API exige todos os campos nos dois). */
@Serializable
data class VideoDeviceRequest(
    val name: String,
    val serial: String,
    val username: String,
    val password: String,
)

// ---- /alarm-centrals ----

@Serializable
data class AlarmCentralDto(
    val id: String,
    val name: String,
    val macAddress: String,
    val password: String,
)

@Serializable
data class AlarmCentralRequest(
    val name: String,
    val macAddress: String,
    val password: String,
)
