package br.com.eduardoamaral.desafiointelbras.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DeviceDao {

    // As leituras vêm da view device_with_favorite (ver DeviceWithFavorite), que já
    // junta devices + favorites. Aqui fica só o que muda em cada consulta.

    /**
     * Busca + filtros feitos no SQLite. Como a view lê as duas tabelas, o Flow
     * emite de novo quando muda um dispositivo OU um favorito.
     */
    @Query(
        """
        SELECT * FROM device_with_favorite
        WHERE name LIKE '%' || :query || '%' ESCAPE '\'
          AND (:type IS NULL OR type = :type)
          AND (:onlyFavorites = 0 OR is_favorite = 1)
        ORDER BY sort_order DESC, id
        """,
    )
    abstract fun observeDevices(
        query: String,
        type: String?,
        onlyFavorites: Boolean,
    ): Flow<List<DeviceWithFavorite>>

    @Query("SELECT * FROM device_with_favorite WHERE id = :id")
    abstract fun observeDevice(id: String): Flow<DeviceWithFavorite?>

    @Query("SELECT * FROM device_with_favorite WHERE id = :id")
    abstract suspend fun getDevice(id: String): DeviceWithFavorite?

    // Lê só devices (não a view) para não reexecutar quando um favorito muda.
    @Query(
        "SELECT COUNT(*) FROM devices WHERE type IN ('VIDEO', 'ALARM') AND sync_state != 'PENDING_DELETE'",
    )
    abstract fun observeCount(): Flow<Int>

    // @Upsert faz INSERT e, se a chave já existe, UPDATE. Diferente de
    // OnConflictStrategy.REPLACE, ele NÃO apaga a linha antiga; um DELETE
    // dispararia o CASCADE e apagaria o favorito.
    // Gravam o sort_order recebido; fora do DAO use upsert/syncType, que cuidam dele.
    @Upsert
    protected abstract suspend fun upsertEntity(device: DeviceEntity)

    @Upsert
    protected abstract suspend fun upsertEntities(devices: List<DeviceEntity>)

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM devices")
    abstract suspend fun maxSortOrder(): Long

    @Query("SELECT * FROM devices WHERE type = :type")
    protected abstract suspend fun getByType(type: String): List<DeviceEntity>

    /**
     * Grava um dispositivo vindo de criação ou edição. Novo vai para o topo da lista;
     * existente mantém a posição (editar não reordena).
     */
    @Transaction
    open suspend fun upsert(device: DeviceEntity) {
        val order = getDeviceEntity(device.id)?.sortOrder ?: (maxSortOrder() + 1)
        upsertEntity(device.copy(sortOrder = order))
    }

    @Query("DELETE FROM devices WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    @Query("DELETE FROM devices WHERE type = :type AND id NOT IN (:ids)")
    abstract suspend fun deleteByTypeExcept(type: String, ids: List<String>)

    @Query("DELETE FROM devices WHERE type = :type")
    abstract suspend fun deleteByType(type: String)

    /**
     * Deixa os dispositivos de um tipo iguais à lista do servidor numa única transação:
     * atualiza/insere o que veio e remove o que sumiu. Cada tipo é sincronizado
     * separadamente porque vem de um endpoint diferente; se um falhar, o outro segue.
     *
     * Itens com operação na fila não são sobrescritos: a versão local é a mais nova.
     * Sem isso, um refresh que rodasse antes da fila enviar um DELETE "ressuscitaria"
     * o item (o servidor ainda o tem e o upsert voltaria o estado para SYNCED).
     *
     * Ordem: quem já existe mantém o sort_order; quem é novo recebe números seguidos, na
     * ordem da resposta (a API lista do mais antigo para o mais novo, então o último fica
     * no topo). Por ser transação, o MAX não se mistura com outra gravação em paralelo.
     */
    @Transaction
    open suspend fun syncType(type: String, devices: List<DeviceEntity>) {
        if (devices.isEmpty()) {
            deleteByType(type)
            return
        }
        val pending = pendingDeviceIds().toSet()
        val knownOrder = getByType(type).associate { it.id to it.sortOrder }
        var next = maxSortOrder()
        val toSave = devices
            .filterNot { it.id in pending }
            .map { device -> device.copy(sortOrder = knownOrder[device.id] ?: ++next) }
        upsertEntities(toSave)
        deleteByTypeExcept(type, devices.map { it.id })
    }

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE device_id = :id)")
    abstract suspend fun isFavorite(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE device_id = :id")
    abstract suspend fun deleteFavorite(id: String)

    @Transaction
    open suspend fun toggleFavorite(id: String) {
        if (isFavorite(id)) deleteFavorite(id) else insertFavorite(FavoriteEntity(id))
    }

    // ---- Fila de operações pendentes (Outbox) ----
    // Cada mudança local e sua entrada na fila são gravadas na MESMA transação:
    // nunca fica um item escondido sem operação na fila, nem o contrário.

    @Query("SELECT device_id FROM pending_operations")
    abstract suspend fun pendingDeviceIds(): List<String>

    @Query("SELECT * FROM pending_operations WHERE device_id = :id")
    abstract suspend fun getPendingOperation(id: String): PendingOperationEntity?

    /** Ordem de envio: da operação mais antiga para a mais nova. */
    @Query("SELECT * FROM pending_operations ORDER BY created_at")
    abstract suspend fun getPendingOperations(): List<PendingOperationEntity>

    /** Para a UI mostrar "N alterações aguardando conexão". */
    @Query("SELECT COUNT(*) FROM pending_operations")
    abstract fun observePendingCount(): Flow<Int>

    @Upsert
    abstract suspend fun upsertPendingOperation(operation: PendingOperationEntity)

    @Query("DELETE FROM pending_operations WHERE device_id = :id")
    abstract suspend fun deletePendingOperation(id: String)

    @Query("UPDATE devices SET sync_state = :state WHERE id = :id")
    abstract suspend fun setSyncState(id: String, state: String)

    /**
     * Remoção otimista: esconde o item e põe a remoção na fila, numa transação só.
     *
     * Combina com o que já estava pendente para o mesmo dispositivo:
     * - criação pendente + remoção = nada a enviar: o servidor nunca soube do item,
     *   então ele é apagado de vez e a criação sai da fila;
     * - edição pendente + remoção = só a remoção (editar algo que vai sumir é inútil);
     * - remoção pendente + remoção = continua uma só, mantendo a posição na fila.
     *
     * @return false se o dispositivo não existe no banco local.
     */
    @Transaction
    open suspend fun enqueueDelete(id: String, createdAt: Long): Boolean {
        val device = getDeviceEntity(id) ?: return false
        val previous = getPendingOperation(id)
        if (previous?.operation == OperationType.CREATE.name) {
            deleteById(id)
            deletePendingOperation(id)
            return true
        }
        setSyncState(id, SyncState.PENDING_DELETE.name)
        upsertPendingOperation(
            PendingOperationEntity(
                deviceId = id,
                operation = OperationType.DELETE.name,
                deviceType = device.type,
                createdAt = previous?.createdAt ?: createdAt,
            ),
        )
        return true
    }

    /** O servidor confirmou a remoção: apaga de vez (o CASCADE leva o favorito junto). */
    @Transaction
    open suspend fun confirmDelete(id: String) {
        deleteById(id)
        deletePendingOperation(id)
    }

    /** O servidor recusou a remoção de vez: o item volta para a lista, com o favorito. */
    @Transaction
    open suspend fun rollbackDelete(id: String) {
        setSyncState(id, SyncState.SYNCED.name)
        deletePendingOperation(id)
    }

    /** Falha temporária: conta a tentativa e mantém na fila. */
    @Query("UPDATE pending_operations SET attempts = attempts + 1 WHERE device_id = :id")
    abstract suspend fun incrementAttempts(id: String)

    /** Lê a linha crua, inclusive se estiver escondida pela view. */
    @Query("SELECT * FROM devices WHERE id = :id")
    abstract suspend fun getDeviceEntity(id: String): DeviceEntity?
}
