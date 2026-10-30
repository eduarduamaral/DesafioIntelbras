package br.com.eduardoamaral.desafiointelbras.domain.repository

import br.com.eduardoamaral.desafiointelbras.domain.model.Device
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceFilter
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceInput
import br.com.eduardoamaral.desafiointelbras.domain.model.Outcome
import br.com.eduardoamaral.desafiointelbras.domain.model.PendingSyncResult
import kotlinx.coroutines.flow.Flow

/**
 * Contrato único de acesso a dispositivos.
 *
 * Leitura: sempre do banco local (single source of truth), via Flow.
 * Escrita: criar e editar vão para a API e, com sucesso, são refletidos no banco local.
 * Remover é otimista: muda o banco na hora e a API recebe depois, pela fila de pendências.
 * Em todos os casos a UI se atualiza sozinha porque está observando o banco.
 */
interface DeviceRepository {
    fun observeDevices(query: String, filter: DeviceFilter): Flow<List<Device>>
    fun observeDevice(id: String): Flow<Device?>

    /** Total cadastrado, independente de filtro e busca (cabeçalho do dashboard). */
    fun observeDeviceCount(): Flow<Int>

    /** Busca vídeos e alarmes na API e sincroniza o banco local, preservando os favoritos. */
    suspend fun refresh(): Outcome<Unit>

    suspend fun create(input: DeviceInput): Outcome<Device>
    suspend fun update(id: String, input: DeviceInput): Outcome<Device>

    /**
     * Remoção otimista: some da lista na hora, com ou sem conexão, e entra na fila de
     * sincronização. Só falha se o dispositivo não existir no banco local.
     */
    suspend fun delete(id: String): Outcome<Unit>

    /**
     * Envia a fila de operações pendentes ao servidor, da mais antiga para a mais nova.
     * Chamado pelo worker em segundo plano quando há conexão.
     */
    suspend fun syncPendingOperations(): PendingSyncResult

    /** Apenas local: nunca vai para o servidor. */
    suspend fun toggleFavorite(id: String)
}
