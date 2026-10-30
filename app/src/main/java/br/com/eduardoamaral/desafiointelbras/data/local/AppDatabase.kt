package br.com.eduardoamaral.desafiointelbras.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Histórico de versões (esquemas exportados em app/schemas):
 * - 1: tabelas devices e favorites.
 * - 2: view device_with_favorite. Migração automática: o Room gera o CREATE VIEW,
 *      sem tocar nos dados.
 * - 3: fila de operações offline: coluna devices.sync_state (padrão SYNCED), tabela
 *      pending_operations e a view passa a esconder remoções pendentes.
 * - 4: coluna devices.sort_order (ordem "mais recentes primeiro"). As linhas existentes
 *      são numeradas pela ordem em que entraram no banco (ver [NumberExistingDevices]).
 * Todas as migrações são automáticas e cobertas por AppDatabaseMigrationTest.
 */
@Database(
    entities = [DeviceEntity::class, FavoriteEntity::class, PendingOperationEntity::class],
    views = [DeviceWithFavorite::class],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4, spec = AppDatabase.NumberExistingDevices::class),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao

    /**
     * Sem isto, todas as linhas antigas ficariam com sort_order = 0, empatadas.
     * O rowid reflete a ordem de inserção (o @Upsert não troca o rowid de quem já existe).
     */
    class NumberExistingDevices : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE devices SET sort_order = rowid")
        }
    }

    companion object {
        const val NAME = "desafio-intelbras.db"
    }
}
