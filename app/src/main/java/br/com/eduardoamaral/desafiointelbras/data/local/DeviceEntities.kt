package br.com.eduardoamaral.desafiointelbras.data.local

import androidx.room.ColumnInfo
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

/** Resultado do JOIN devices + favorites. */
data class DeviceWithFavorite(
    @Embedded val device: DeviceEntity,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
)
