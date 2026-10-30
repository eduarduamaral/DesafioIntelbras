package br.com.eduardoamaral.desafiointelbras.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Teste de integração com o SQLite real (em memória): garante que a estratégia de
 * favoritos funciona de verdade, inclusive o CASCADE e o @Upsert.
 */
@RunWith(AndroidJUnit4::class)
class DeviceDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: DeviceDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        dao = database.deviceDao()
    }

    @After
    fun tearDown() = database.close()

    private fun video(id: String, name: String) =
        DeviceEntity(id, "VIDEO", name, password = "1234", serial = "SN$id", username = "admin")

    private fun alarm(id: String, name: String) =
        DeviceEntity(id, "ALARM", name, password = "1234", macAddress = "1A2B3C4D5E6F")

    private suspend fun allIds() =
        dao.observeDevices(query = "", type = null, onlyFavorites = false).first().map { it.device.id }

    @Test
    fun syncPreservaFavoritoQuandoDispositivoContinuaNoServidor() = runTest {
        dao.syncType("VIDEO", listOf(video("1", "Câmera")))
        dao.toggleFavorite("1")

        dao.syncType("VIDEO", listOf(video("1", "Câmera renomeada")))

        val device = dao.getDevice("1")!!
        assertEquals("Câmera renomeada", device.device.name)
        assertTrue(device.isFavorite)
    }

    @Test
    fun syncRemoveDispositivoQueSumiuDoServidorJuntoComOFavorito() = runTest {
        dao.syncType("VIDEO", listOf(video("1", "A"), video("2", "B")))
        dao.toggleFavorite("2")

        dao.syncType("VIDEO", listOf(video("1", "A")))

        assertEquals(listOf("1"), allIds())
        assertFalse(dao.isFavorite("2"))
    }

    @Test
    fun syncDeUmTipoNaoApagaOOutro() = runTest {
        dao.syncType("VIDEO", listOf(video("v1", "Câmera")))
        dao.syncType("ALARM", listOf(alarm("a1", "Central")))

        dao.syncType("VIDEO", emptyList())

        assertEquals(listOf("a1"), allIds())
    }

    @Test
    fun filtrosDeTipoFavoritoEBusca() = runTest {
        dao.syncType("VIDEO", listOf(video("1", "Câmera garagem"), video("3", "Câmera loja")))
        dao.syncType("ALARM", listOf(alarm("2", "Central loja")))
        dao.toggleFavorite("3")

        // Mais recente primeiro: "3" veio depois de "1" na resposta do servidor.
        assertEquals(
            listOf("3", "1"),
            dao.observeDevices("", "VIDEO", false).first().map { it.device.id },
        )
        assertEquals(listOf("3"), dao.observeDevices("", null, true).first().map { it.device.id })
        assertEquals(
            listOf("2", "3"),
            dao.observeDevices("loja", null, false).first().map { it.device.id },
        )
    }

    @Test
    fun buscaComCuringaDoLikeEhTratadaComoTexto() = runTest {
        dao.syncType("VIDEO", listOf(video("1", "Câmera 100%"), video("2", "Câmera 1000")))

        // O data source escapa "%"; aqui simulamos o valor já escapado.
        val result = dao.observeDevices("100\\%", null, false).first().map { it.device.id }

        assertEquals(listOf("1"), result)
    }

    @Test
    fun contagemAcompanhaSincronizacaoERemocao() = runTest {
        assertEquals(0, dao.observeCount().first())

        dao.syncType("VIDEO", listOf(video("v1", "A"), video("v2", "B")))
        dao.syncType("ALARM", listOf(alarm("a1", "C")))
        assertEquals(3, dao.observeCount().first())

        dao.deleteById("v1")
        assertEquals(2, dao.observeCount().first())

        dao.syncType("ALARM", emptyList())
        assertEquals(1, dao.observeCount().first())
    }

    @Test
    fun contagemNaoMudaAoMarcarFavorito() = runTest {
        dao.syncType("VIDEO", listOf(video("v1", "A"), video("v2", "B")))

        dao.toggleFavorite("v1")

        // O total é de dispositivos, não de favoritos. A query só lê a tabela devices,
        // então o Room nem reexecuta a contagem quando favorites muda.
        assertEquals(2, dao.observeCount().first())
    }

    @Test
    fun contagemIgnoraLinhaComTipoDesconhecido() = runTest {
        dao.syncType("VIDEO", listOf(video("v1", "A")))
        dao.upsert(DeviceEntity("x1", "OTHER", "Tipo inválido", password = "1234"))

        // Mesmo critério do mapper: o que a lista não exibe não entra no total.
        assertEquals(1, dao.observeCount().first())
    }

    // ---- Remoção otimista e fila (Outbox) ----

    @Test
    fun remocaoPendenteEscondeDaListaEDaContagemMasMantemALinha() = runTest {
        dao.syncType("VIDEO", listOf(video("v1", "A"), video("v2", "B")))

        assertTrue(dao.enqueueDelete("v1", createdAt = 1))

        assertEquals(listOf("v2"), allIds())
        assertEquals(1, dao.observeCount().first())
        assertEquals(null, dao.getDevice("v1")) // a view não mostra
        assertEquals(SyncState.PENDING_DELETE.name, dao.getDeviceEntity("v1")?.syncState) // a linha existe
        assertEquals(OperationType.DELETE.name, dao.getPendingOperation("v1")?.operation)
    }

    @Test
    fun syncNaoRessuscitaItemComRemocaoPendente() = runTest {
        dao.syncType("VIDEO", listOf(video("v1", "A")))
        dao.enqueueDelete("v1", createdAt = 1)

        // O refresh roda antes da fila enviar o DELETE: o servidor ainda tem o item.
        dao.syncType("VIDEO", listOf(video("v1", "A renomeado no servidor")))

        assertTrue(allIds().isEmpty())
        assertEquals(SyncState.PENDING_DELETE.name, dao.getDeviceEntity("v1")?.syncState)
        assertEquals(1, dao.getPendingOperations().size)
    }

    @Test
    fun confirmarRemocaoApagaALinhaOFavoritoEAOperacao() = runTest {
        dao.syncType("VIDEO", listOf(video("v1", "A")))
        dao.toggleFavorite("v1")
        dao.enqueueDelete("v1", createdAt = 1)

        dao.confirmDelete("v1")

        assertEquals(null, dao.getDeviceEntity("v1"))
        assertFalse(dao.isFavorite("v1"))
        assertTrue(dao.getPendingOperations().isEmpty())
    }

    @Test
    fun desfazerRemocaoDevolveOItemComOFavorito() = runTest {
        dao.syncType("VIDEO", listOf(video("v1", "A")))
        dao.toggleFavorite("v1")
        dao.enqueueDelete("v1", createdAt = 1)

        dao.rollbackDelete("v1")

        val device = dao.getDevice("v1")!!
        assertTrue(device.isFavorite)
        assertEquals(SyncState.SYNCED.name, device.device.syncState)
        assertTrue(dao.getPendingOperations().isEmpty())
    }

    @Test
    fun removerDuasVezesMantemUmaOperacaoNaPosicaoOriginal() = runTest {
        dao.syncType("VIDEO", listOf(video("v1", "A")))
        dao.enqueueDelete("v1", createdAt = 10)

        dao.enqueueDelete("v1", createdAt = 99)

        val operations = dao.getPendingOperations()
        assertEquals(1, operations.size)
        assertEquals(10L, operations.single().createdAt)
    }

    @Test
    fun removerItemComCriacaoPendenteNaoEnviaNada() = runTest {
        // Item criado offline: existe só no app.
        dao.upsert(video("tmp-1", "Novo").copy(syncState = SyncState.PENDING_CREATE.name))
        dao.upsertPendingOperation(
            PendingOperationEntity("tmp-1", OperationType.CREATE.name, "VIDEO", createdAt = 1),
        )

        dao.enqueueDelete("tmp-1", createdAt = 2)

        assertEquals(null, dao.getDeviceEntity("tmp-1"))
        assertTrue(dao.getPendingOperations().isEmpty())
    }

    @Test
    fun filaEhLidaDaOperacaoMaisAntigaParaAMaisNova() = runTest {
        dao.syncType("VIDEO", listOf(video("v1", "A"), video("v2", "B")))
        dao.enqueueDelete("v2", createdAt = 5)
        dao.enqueueDelete("v1", createdAt = 7)

        assertEquals(listOf("v2", "v1"), dao.getPendingOperations().map { it.deviceId })
    }

    @Test
    fun removerItemInexistenteRetornaFalse() = runTest {
        assertFalse(dao.enqueueDelete("nao-existe", createdAt = 1))
        assertTrue(dao.getPendingOperations().isEmpty())
    }

    @Test
    fun listaMostraOsAdicionadosMaisRecentementePrimeiro() = runTest {
        dao.syncType("VIDEO", listOf(video("v1", "A"), video("v2", "B")))
        dao.syncType("ALARM", listOf(alarm("a1", "C")))
        assertEquals(listOf("a1", "v2", "v1"), allIds())

        dao.upsert(video("v3", "Criado no app"))
        assertEquals(listOf("v3", "a1", "v2", "v1"), allIds())
    }

    @Test
    fun edicaoESincronizacaoNaoMudamAPosicao() = runTest {
        dao.syncType("VIDEO", listOf(video("v1", "A"), video("v2", "B")))

        dao.upsert(video("v1", "A editado"))
        // O servidor mudou a ordem da resposta: quem já existe não se move.
        dao.syncType("VIDEO", listOf(video("v2", "B"), video("v1", "A editado")))

        assertEquals(listOf("v2", "v1"), allIds())
        assertEquals("A editado", dao.getDevice("v1")!!.device.name)
    }

    @Test
    fun itemNovoVindoDoServidorEntraNoTopo() = runTest {
        dao.syncType("VIDEO", listOf(video("v1", "A")))
        dao.upsert(alarm("a1", "Criado no app"))

        dao.syncType("VIDEO", listOf(video("v1", "A"), video("v9", "Criado em outro aparelho")))

        assertEquals(listOf("v9", "a1", "v1"), allIds())
    }
}
