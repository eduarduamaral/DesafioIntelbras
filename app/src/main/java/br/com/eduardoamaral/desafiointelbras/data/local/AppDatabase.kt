package br.com.eduardoamaral.desafiointelbras.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Histórico de versões (esquemas exportados em app/schemas):
 * - 1: tabelas devices e favorites.
 * - 2: view device_with_favorite. Migração automática: o Room gera o CREATE VIEW,
 *      sem tocar nos dados.
 * - 3: fila de operações offline: coluna devices.sync_state (padrão SYNCED), tabela
 *      pending_operations e a view passa a esconder remoções pendentes.
 * Todas as migrações são automáticas e cobertas por AppDatabaseMigrationTest.
 */
@Database(
    entities = [DeviceEntity::class, FavoriteEntity::class, PendingOperationEntity::class],
    views = [DeviceWithFavorite::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao

    companion object {
        const val NAME = "desafio-intelbras.db"
    }
}
