package br.com.eduardoamaral.desafiointelbras.data.remote

import br.com.eduardoamaral.desafiointelbras.data.remote.dto.AlarmCentralDto
import br.com.eduardoamaral.desafiointelbras.data.remote.dto.AlarmCentralRequest
import br.com.eduardoamaral.desafiointelbras.data.remote.dto.ListResponse
import br.com.eduardoamaral.desafiointelbras.data.remote.dto.VideoDeviceDto
import br.com.eduardoamaral.desafiointelbras.data.remote.dto.VideoDeviceRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart

/**
 * Endpoints da API, espelhando a documentação (/api), sem nenhum detalhe de HTTP.
 * Resposta fora de 2xx chega como [HttpStatusException] e é convertida em safeApiCall.
 * Os GET por id não são usados: a tela de informações lê do banco local.
 */
interface DeviceApi {
    suspend fun getVideoDevices(): ListResponse<VideoDeviceDto>
    suspend fun createVideoDevice(body: VideoDeviceRequest): VideoDeviceDto
    suspend fun updateVideoDevice(id: String, body: VideoDeviceRequest): VideoDeviceDto
    suspend fun deleteVideoDevice(id: String)

    suspend fun getAlarmCentrals(): ListResponse<AlarmCentralDto>
    suspend fun createAlarmCentral(body: AlarmCentralRequest): AlarmCentralDto
    suspend fun updateAlarmCentral(id: String, body: AlarmCentralRequest): AlarmCentralDto
    suspend fun deleteAlarmCentral(id: String)
}

/** Implementação com Ktor. URL base, token, JSON e validação de status vêm do [HttpClient] (createHttpClient). */
class KtorDeviceApi(private val client: HttpClient) : DeviceApi {

    override suspend fun getVideoDevices(): ListResponse<VideoDeviceDto> = client.get(VIDEO).body()

    override suspend fun createVideoDevice(body: VideoDeviceRequest): VideoDeviceDto =
        client.post(VIDEO) { json(body) }.body()

    override suspend fun updateVideoDevice(id: String, body: VideoDeviceRequest): VideoDeviceDto =
        client.patch(path(VIDEO, id)) { json(body) }.body()

    override suspend fun deleteVideoDevice(id: String) {
        client.delete(path(VIDEO, id))
    }

    override suspend fun getAlarmCentrals(): ListResponse<AlarmCentralDto> = client.get(ALARM).body()

    override suspend fun createAlarmCentral(body: AlarmCentralRequest): AlarmCentralDto =
        client.post(ALARM) { json(body) }.body()

    override suspend fun updateAlarmCentral(id: String, body: AlarmCentralRequest): AlarmCentralDto =
        client.patch(path(ALARM, id)) { json(body) }.body()

    override suspend fun deleteAlarmCentral(id: String) {
        client.delete(path(ALARM, id))
    }

    private companion object {
        // Relativos: o defaultRequest do HttpClient junta com a URL base.
        const val VIDEO = "video-devices"
        const val ALARM = "alarm-centrals"

        fun path(resource: String, id: String) = "$resource/${id.encodeURLPathPart()}"
    }
}

private inline fun <reified T> HttpRequestBuilder.json(body: T) {
    contentType(ContentType.Application.Json)
    setBody(body)
}
