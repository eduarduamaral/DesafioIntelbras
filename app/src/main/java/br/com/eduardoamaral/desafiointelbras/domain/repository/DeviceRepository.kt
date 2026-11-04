package br.com.eduardoamaral.desafiointelbras.domain.repository

import br.com.eduardoamaral.desafiointelbras.domain.model.Device
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceFilter
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceInput
import br.com.eduardoamaral.desafiointelbras.domain.model.Outcome
import br.com.eduardoamaral.desafiointelbras.domain.model.PendingSyncResult
import br.com.eduardoamaral.desafiointelbras.domain.model.SyncEvent
import br.com.eduardoamaral.desafiointelbras.domain.model.WriteResult
import kotlinx.coroutines.flow.Flow

/**
 * Contrato único de acesso a dispositivos.
 *
 * Leitura: sempre do banco local (single source of truth), via Flow.
 * Escrita: criar e editar tentam a API primeiro (para erros como 409 aparecerem no
 * formulário na hora); sem rede, gravam no banco e vão para a fila de pendências.
 * Remover é otimista: muda o banco na hora e a API recebe depois, pela fila.
 * Em todos os casos a UI se atualiza sozinha porque está observando o banco.
 */
interface DeviceRepository {
    fun observeDevices(query: String, filter: DeviceFilter): Flow<List<Device>>
    fun observeDevice(id: String): Flow<Device?>

    /** Total cadastrado, independente de filtro e busca (cabeçalho do dashboard). */
    fun observeDeviceCount(): Flow<Int>

    /** Busca vídeos e alarmes na API e sincroniza o banco local, preservando os favoritos. */
    suspend fun refresh(): Outcome<Unit>

    /** Sucesso também sem rede ([WriteResult.Queued]); falha se o servidor recusar os dados. */
    suspend fun create(input: DeviceInput): Outcome<WriteResult>
    suspend fun update(id: String, input: DeviceInput): Outcome<WriteResult>

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

    /** Quantas alterações ainda não chegaram ao servidor. */
    fun observePendingCount(): Flow<Int>

    /**
     * Avisos da sincronização (ex.: remoção recusada). Quem estiver observando recebe;
     * se ninguém estiver (app fechado), o efeito continua visível na lista.
     */
    val syncEvents: Flow<SyncEvent>

    /** Apenas local: nunca vai para o servidor. */
    suspend fun toggleFavorite(id: String)
}
