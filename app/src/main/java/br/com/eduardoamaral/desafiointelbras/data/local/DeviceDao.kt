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

    /**
     * Busca + filtros feitos no SQLite. Como a query lê as duas tabelas, o Flow
     * emite de novo quando muda um dispositivo OU um favorito.
     */
    @Query(
        """
        SELECT d.*, (f.device_id IS NOT NULL) AS is_favorite
        FROM devices d
        LEFT JOIN favorites f ON f.device_id = d.id
        WHERE d.name LIKE '%' || :query || '%' ESCAPE '\'
          AND (:type IS NULL OR d.type = :type)
          AND (:onlyFavorites = 0 OR f.device_id IS NOT NULL)
        ORDER BY d.name COLLATE NOCASE
        """,
    )
    abstract fun observeDevices(
        query: String,
        type: String?,
        onlyFavorites: Boolean,
    ): Flow<List<DeviceWithFavorite>>

    @Query(
        """
        SELECT d.*, (f.device_id IS NOT NULL) AS is_favorite
        FROM devices d
        LEFT JOIN favorites f ON f.device_id = d.id
        WHERE d.id = :id
        """,
    )
    abstract fun observeDevice(id: String): Flow<DeviceWithFavorite?>

    @Query(
        """
        SELECT d.*, (f.device_id IS NOT NULL) AS is_favorite
        FROM devices d
        LEFT JOIN favorites f ON f.device_id = d.id
        WHERE d.id = :id
        """,
    )
    abstract suspend fun getDevice(id: String): DeviceWithFavorite?

    @Query("SELECT COUNT(*) FROM devices WHERE type IN ('VIDEO', 'ALARM')")
    abstract fun observeCount(): Flow<Int>

    // @Upsert faz INSERT e, se a chave já existe, UPDATE. Diferente de
    // OnConflictStrategy.REPLACE, ele NÃO apaga a linha antiga; um DELETE
    // dispararia o CASCADE e apagaria o favorito.
    @Upsert
    abstract suspend fun upsert(device: DeviceEntity)

    @Upsert
    abstract suspend fun upsertAll(devices: List<DeviceEntity>)

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
     */
    @Transaction
    open suspend fun syncType(type: String, devices: List<DeviceEntity>) {
        if (devices.isEmpty()) {
            deleteByType(type)
        } else {
            upsertAll(devices)
            deleteByTypeExcept(type, devices.map { it.id })
        }
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
}
