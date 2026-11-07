package br.com.eduardoamaral.desafiointelbras.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Estado de sincronização de um dispositivo no banco local.
 * SYNCED: igual ao servidor. Os demais indicam uma operação na fila ainda não confirmada.
 * PENDING_CREATE e PENDING_UPDATE já estão previstos para criar/editar offline.
 */
enum class SyncState { SYNCED, PENDING_CREATE, PENDING_UPDATE, PENDING_DELETE }

enum class OperationType { CREATE, UPDATE, DELETE }

/**
 * Fila de operações pendentes (padrão Outbox).
 *
 * - Uma linha por dispositivo (device_id é a chave): uma nova ação sobre o mesmo dispositivo
 *   é combinada com a anterior em vez de empilhada (ver DeviceDao.enqueueDelete).
 * - Sem chave estrangeira para devices de propósito: a operação precisa sobreviver mesmo
 *   quando a linha do dispositivo está escondida ou ainda não tem id definitivo.
 * - device_type guarda o tipo porque cada tipo tem seu próprio endpoint na API.
 * - payload (JSON) fica reservado para criar/editar offline; a remoção não precisa.
 */
@Entity(tableName = "pending_operations")
data class PendingOperationEntity(
    @PrimaryKey @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "operation") val operation: String,
    @ColumnInfo(name = "device_type") val deviceType: String,
    val payload: String? = null,
    /** Tentativas que falharam por erro temporário (rede, timeout, 5xx). */
    val attempts: Int = 0,
    /** Último erro definitivo, para o usuário decidir entre tentar de novo ou descartar. */
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    /** Ordem de envio: a fila é processada da mais antiga para a mais nova. */
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
