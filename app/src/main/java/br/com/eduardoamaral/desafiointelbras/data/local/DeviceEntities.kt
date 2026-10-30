package br.com.eduardoamaral.desafiointelbras.data.local

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Espelho do que veio da API. Vídeo e alarme ficam na MESMA tabela (single-table),
 * com colunas específicas anuláveis: listagem, busca e filtros viram uma query só.
 * Trade-off: colunas nulas conforme o tipo; o mapper garante a consistência.
 *
 * Os ids da API são UUIDs, então não há colisão entre os dois recursos.
 */
@Entity(tableName = "devices", indices = [Index("type")])
data class DeviceEntity(
    @PrimaryKey val id: String,
    /** DeviceType.name */
    val type: String,
    val name: String,
    val password: String,
    val serial: String? = null,
    val username: String? = null,
    @ColumnInfo(name = "mac_address") val macAddress: String? = null,
    /**
     * SyncState.name. O valor padrão no banco faz a migração preencher as linhas que já
     * existiam como SYNCED. Dados vindos do servidor entram sempre como SYNCED.
     */
    @ColumnInfo(name = "sync_state", defaultValue = "SYNCED")
    val syncState: String = SyncState.SYNCED.name,
)

/**
 * Favoritos ficam numa tabela separada, que pertence só ao app.
 * A sincronização nunca escreve aqui, por isso os favoritos sobrevivem a qualquer refresh.
 * O CASCADE remove o favorito quando o dispositivo deixa de existir.
 */
@Entity(
    tableName = "favorites",
    foreignKeys = [
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["device_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class FavoriteEntity(
    @PrimaryKey @ColumnInfo(name = "device_id") val deviceId: String,
)

/**
 * View do banco com os dispositivos VISÍVEIS e se cada um é favorito.
 *
 * Visível = não está com remoção pendente. Com a remoção otimista, o item some da tela na
 * hora, mas a linha só é apagada quando o servidor confirma (se ele recusar, o item volta).
 *
 * A regra "é favorito = existe linha em favorites" fica definida só aqui; as queries do
 * DAO leem desta view e só acrescentam o WHERE. O Room sabe que a view lê devices e
 * favorites, então os Flow continuam emitindo quando qualquer uma das duas muda.
 * O SQLite expande a view dentro da query: o plano de execução é o mesmo do JOIN direto.
 */
@DatabaseView(
    viewName = DeviceWithFavorite.VIEW_NAME,
    value = """
        SELECT d.*, (f.device_id IS NOT NULL) AS is_favorite
        FROM devices d
        LEFT JOIN favorites f ON f.device_id = d.id
        WHERE d.sync_state != 'PENDING_DELETE'
    """,
)
data class DeviceWithFavorite(
    @Embedded val device: DeviceEntity,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
) {
    companion object {
        const val VIEW_NAME = "device_with_favorite"
    }
}
