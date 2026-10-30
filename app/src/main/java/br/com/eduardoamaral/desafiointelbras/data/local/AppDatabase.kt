package br.com.eduardoamaral.desafiointelbras.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Histórico de versões (esquemas exportados em app/schemas):
 * - 1: tabelas devices e favorites.
 * - 2: view device_with_favorite. Migração automática: o Room gera o CREATE VIEW,
 *      sem tocar nos dados. Coberta por AppDatabaseMigrationTest.
 */
@Database(
    entities = [DeviceEntity::class, FavoriteEntity::class],
    views = [DeviceWithFavorite::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao

    companion object {
        const val NAME = "desafio-intelbras.db"
    }
}
