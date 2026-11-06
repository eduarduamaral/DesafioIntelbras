package br.com.eduardoamaral.desafiointelbras.data.remote

import br.com.eduardoamaral.desafiointelbras.data.remote.dto.AlarmCentralDto
import br.com.eduardoamaral.desafiointelbras.data.remote.dto.AlarmCentralRequest
import br.com.eduardoamaral.desafiointelbras.data.remote.dto.ListResponse
import br.com.eduardoamaral.desafiointelbras.data.remote.dto.VideoDeviceDto
import br.com.eduardoamaral.desafiointelbras.data.remote.dto.VideoDeviceRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Fonte remota, espelhando a documentação (/api).
 * Erros HTTP chegam como retrofit2.HttpException e são convertidos em safeApiCall.
 * Os GET por id não são usados: a tela de informações lê do banco local.
 */
interface DeviceApi {

    @GET("video-devices")
    suspend fun getVideoDevices(): ListResponse<VideoDeviceDto>

    @POST("video-devices")
    suspend fun createVideoDevice(@Body body: VideoDeviceRequest): VideoDeviceDto

    @PATCH("video-devices/{id}")
    suspend fun updateVideoDevice(@Path("id") id: String, @Body body: VideoDeviceRequest): VideoDeviceDto

    @DELETE("video-devices/{id}")
    suspend fun deleteVideoDevice(@Path("id") id: String)

    @GET("alarm-centrals")
    suspend fun getAlarmCentrals(): ListResponse<AlarmCentralDto>

    @POST("alarm-centrals")
    suspend fun createAlarmCentral(@Body body: AlarmCentralRequest): AlarmCentralDto

    @PATCH("alarm-centrals/{id}")
    suspend fun updateAlarmCentral(@Path("id") id: String, @Body body: AlarmCentralRequest): AlarmCentralDto

    @DELETE("alarm-centrals/{id}")
    suspend fun deleteAlarmCentral(@Path("id") id: String)
}
