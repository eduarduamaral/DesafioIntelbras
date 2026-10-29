package br.com.eduardoamaral.desafiointelbras.data.remote

import br.com.eduardoamaral.desafiointelbras.data.mapper.toDomain
import br.com.eduardoamaral.desafiointelbras.data.mapper.toRequest
import br.com.eduardoamaral.desafiointelbras.domain.model.Device
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceInput
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceType
import br.com.eduardoamaral.desafiointelbras.domain.model.Outcome
import javax.inject.Inject

/**
 * Fonte remota, simétrica à [br.com.eduardoamaral.desafiointelbras.data.local.DeviceLocalDataSource]:
 * fala em modelos de domínio e devolve [Outcome].
 *
 * Esconde do repositório tudo o que é detalhe da API: os dois recursos separados
 * (/video-devices e /alarm-centrals), os DTOs, o envelope { count, data } e a tradução
 * de exceções HTTP/rede em AppError. O repositório fica só com a orquestração
 * (fonte única de verdade, sincronização e concorrência).
 */
interface DeviceRemoteDataSource {
    suspend fun fetchDevices(type: DeviceType): Outcome<List<Device>>
    suspend fun create(input: DeviceInput): Outcome<Device>
    suspend fun update(id: String, input: DeviceInput): Outcome<Device>

    /** O tipo é necessário porque cada tipo tem seu próprio endpoint de remoção. */
    suspend fun delete(id: String, type: DeviceType): Outcome<Unit>
}

class RetrofitDeviceRemoteDataSource @Inject constructor(
    private val api: DeviceApi,
) : DeviceRemoteDataSource {

    override suspend fun fetchDevices(type: DeviceType): Outcome<List<Device>> = safeApiCall {
        when (type) {
            DeviceType.VIDEO -> api.getVideoDevices().data.map { it.toDomain() }
            DeviceType.ALARM -> api.getAlarmCentrals().data.map { it.toDomain() }
        }
    }

    override suspend fun create(input: DeviceInput): Outcome<Device> = safeApiCall {
        when (input) {
            is DeviceInput.Video -> api.createVideoDevice(input.toRequest()).toDomain()
            is DeviceInput.Alarm -> api.createAlarmCentral(input.toRequest()).toDomain()
        }
    }

    override suspend fun update(id: String, input: DeviceInput): Outcome<Device> = safeApiCall {
        when (input) {
            is DeviceInput.Video -> api.updateVideoDevice(id, input.toRequest()).toDomain()
            is DeviceInput.Alarm -> api.updateAlarmCentral(id, input.toRequest()).toDomain()
        }
    }

    override suspend fun delete(id: String, type: DeviceType): Outcome<Unit> = safeApiCall {
        when (type) {
            DeviceType.VIDEO -> api.deleteVideoDevice(id)
            DeviceType.ALARM -> api.deleteAlarmCentral(id)
        }
    }
}
