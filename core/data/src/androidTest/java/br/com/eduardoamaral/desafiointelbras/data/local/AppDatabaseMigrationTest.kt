package br.com.eduardoamaral.desafiointelbras.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Garante que quem já tem o app instalado (banco na versão 1) não perde nada ao atualizar.
 *
 * O MigrationTestHelper cria o banco exatamente como era na versão 1 (a partir de
 * app/schemas/.../1.json), roda as migrações e valida o resultado contra o esquema 2.json.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migra1Para2PreservandoDispositivosEFavoritos() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO devices (id, type, name, password, serial, username, mac_address)
                VALUES ('v1', 'VIDEO', 'Câmera', '1234', 'SN1', 'admin', NULL),
                       ('a1', 'ALARM', 'Central', '123456', NULL, NULL, '1A2B3C4D5E6F')
                """.trimIndent(),
            )
            execSQL("INSERT INTO favorites (device_id) VALUES ('v1')")
            close()
        }

        // validateDroppedTables = true: falha se a migração apagar alguma tabela.
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true)

        // A view nova existe e enxerga os dados que já estavam no banco, com o favorito.
        db.query("SELECT id, is_favorite FROM device_with_favorite ORDER BY id").use { cursor ->
            val rows = buildList {
                while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getInt(1))
            }
            assertEquals(listOf("a1" to 0, "v1" to 1), rows)
        }
        db.close()
    }

    @Test
    fun migra2Para3MarcandoTudoComoSincronizadoECriandoAFila() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                """
                INSERT INTO devices (id, type, name, password, serial, username, mac_address)
                VALUES ('v1', 'VIDEO', 'Câmera', '1234', 'SN1', 'admin', NULL)
                """.trimIndent(),
            )
            execSQL("INSERT INTO favorites (device_id) VALUES ('v1')")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true)

        // Linhas antigas recebem o valor padrão da coluna nova.
        db.query("SELECT sync_state FROM devices WHERE id = 'v1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("SYNCED", cursor.getString(0))
        }
        // A fila nasce vazia e o item continua visível na view, com o favorito.
        db.query("SELECT COUNT(*) FROM pending_operations").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        db.query("SELECT is_favorite FROM device_with_favorite WHERE id = 'v1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        db.close()
    }

    @Test
    fun migra3Para4NumerandoAsLinhasPelaOrdemDeInsercao() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                """
                INSERT INTO devices (id, type, name, password, serial, username, mac_address)
                VALUES ('v1', 'VIDEO', 'Câmera', '1234', 'SN1', 'admin', NULL),
                       ('a1', 'ALARM', 'Central', '123456', NULL, NULL, '1A2B3C4D5E6F')
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true)

        // Sem empate: o último inserido (a1) fica no topo.
        db.query("SELECT id, sort_order FROM devices ORDER BY sort_order DESC").use { cursor ->
            val rows = buildList {
                while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getLong(1))
            }
            assertEquals(listOf("a1" to 2L, "v1" to 1L), rows)
        }
        db.close()
    }

    @Test
    fun migra1AteAVersaoAtualEmSequencia() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO devices (id, type, name, password, serial, username, mac_address)
                VALUES ('a1', 'ALARM', 'Central', '123456', NULL, NULL, '1A2B3C4D5E6F')
                """.trimIndent(),
            )
            close()
        }

        // Quem ficou várias versões sem atualizar também precisa chegar inteiro na atual.
        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true)

        db.query("SELECT id FROM device_with_favorite").use { cursor ->
            cursor.moveToFirst()
            assertEquals("a1", cursor.getString(0))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
