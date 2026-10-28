package br.com.eduardoamaral.desafiointelbras.domain.repository

import br.com.eduardoamaral.desafiointelbras.domain.model.Device
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceFilter
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceInput
import br.com.eduardoamaral.desafiointelbras.domain.model.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Contrato único de acesso a dispositivos.
 *
 * Leitura: sempre do banco local (single source of truth), via Flow.
 * Escrita: vai para a API e, em caso de sucesso, é refletida no banco local;
 * a UI se atualiza sozinha porque está observando o banco.
 */
interface DeviceRepository {
    fun observeDevices(query: String, filter: DeviceFilter): Flow<List<Device>>
    fun observeDevice(id: String): Flow<Device?>

    /** Busca vídeos e alarmes na API e sincroniza o banco local, preservando os favoritos. */
    suspend fun refresh(): Outcome<Unit>

    suspend fun create(input: DeviceInput): Outcome<Device>
    suspend fun update(id: String, input: DeviceInput): Outcome<Device>
    suspend fun delete(id: String): Outcome<Unit>

    /** Apenas local: nunca vai para o servidor. */
    suspend fun toggleFavorite(id: String)
}
