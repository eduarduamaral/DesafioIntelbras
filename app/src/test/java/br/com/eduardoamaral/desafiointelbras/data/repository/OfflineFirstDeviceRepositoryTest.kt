package br.com.eduardoamaral.desafiointelbras.data.repository

import br.com.eduardoamaral.desafiointelbras.data.remote.RetrofitDeviceRemoteDataSource
import br.com.eduardoamaral.desafiointelbras.data.remote.dto.AlarmCentralDto
import br.com.eduardoamaral.desafiointelbras.data.remote.dto.VideoDeviceDto
import br.com.eduardoamaral.desafiointelbras.domain.model.AppError
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceDetails
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceInput
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceType
import br.com.eduardoamaral.desafiointelbras.domain.model.Outcome
import br.com.eduardoamaral.desafiointelbras.domain.model.PendingSyncResult
import br.com.eduardoamaral.desafiointelbras.domain.model.SyncEvent
import br.com.eduardoamaral.desafiointelbras.testutil.FakeDeviceApi
import br.com.eduardoamaral.desafiointelbras.testutil.FakeDeviceLocalDataSource
import br.com.eduardoamaral.desafiointelbras.testutil.FakeSyncScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirstDeviceRepositoryTest {

    private lateinit var api: FakeDeviceApi
    private lateinit var local: FakeDeviceLocalDataSource
    private lateinit var scheduler: FakeSyncScheduler
    private lateinit var repository: OfflineFirstDeviceRepository

    private val camera = VideoDeviceDto("v1", "Câmera", "ABC123", "admin", "1234")
    private val central = AlarmCentralDto("a1", "Central", "1A2B3C4D5E6F", "1234")

    @Before
    fun setUp() {
        api = FakeDeviceApi()
        local = FakeDeviceLocalDataSource()
        // Fonte remota real sobre a API falsa: os testes cobrem também a conversão de DTOs
        // e a tradução de erros HTTP feitas pela RetrofitDeviceRemoteDataSource.
        scheduler = FakeSyncScheduler()
        repository = OfflineFirstDeviceRepository(RetrofitDeviceRemoteDataSource(api), local, scheduler)
    }

    private fun httpError(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    @Test
    fun `refresh junta videos e alarmes no banco local`() = runTest {
        api.videos += camera
        api.alarms += central

        val result = repository.refresh()

        assertTrue(result is Outcome.Success)
        val byId = local.snapshot.associateBy { it.id }
        assertEquals(DeviceDetails.Video("ABC123", "admin", "1234"), byId.getValue("v1").details)
        assertEquals(DeviceDetails.Alarm("1A2B3C4D5E6F", "1234"), byId.getValue("a1").details)
    }

    @Test
    fun `refresh preserva favoritos dos dispositivos que continuam no servidor`() = runTest {
        api.videos += camera
        repository.refresh()
        repository.toggleFavorite("v1")

        api.videos[0] = camera.copy(name = "Câmera renomeada")
        repository.refresh()

        val device = local.snapshot.single()
        assertEquals("Câmera renomeada", device.name)
        assertTrue(device.isFavorite)
    }

    @Test
    fun `refresh remove o que sumiu do servidor`() = runTest {
        api.videos += camera
        api.alarms += central
        repository.refresh()

        api.alarms.clear()
        repository.refresh()

        assertEquals(listOf("v1"), local.snapshot.map { it.id })
    }

    @Test
    fun `falha em um endpoint nao impede a sincronizacao do outro`() = runTest {
        api.videos += camera
        api.alarms += central
        repository.refresh()

        api.alarms.clear()
        api.videos += camera.copy(id = "v2", name = "Nova")
        api.alarmError = FakeDeviceApi.NETWORK_ERROR
        val result = repository.refresh()

        assertEquals(Outcome.Failure(AppError.Network), result)
        // vídeos atualizados; alarmes mantidos como estavam (último sync bem-sucedido)
        assertEquals(setOf("v1", "v2", "a1"), local.snapshot.map { it.id }.toSet())
    }

    @Test
    fun `refresh com erro de rede mantem os dados locais`() = runTest {
        api.videos += camera
        repository.refresh()

        api.videoError = FakeDeviceApi.NETWORK_ERROR
        api.alarmError = FakeDeviceApi.NETWORK_ERROR
        val result = repository.refresh()

        assertEquals(Outcome.Failure(AppError.Network), result)
        assertEquals(1, local.snapshot.size)
    }

    @Test
    fun `403 vira Unauthorized`() = runTest {
        api.videoError = httpError(403)
        api.alarmError = httpError(403)

        assertEquals(Outcome.Failure(AppError.Unauthorized), repository.refresh())
    }

    @Test
    fun `create de alarme normaliza o MAC e grava no banco local`() = runTest {
        val result = repository.create(DeviceInput.Alarm("  Casa ", "1a:2b:3c:4d:5e:6f", "123456"))

        assertTrue(result is Outcome.Success)
        assertEquals("1A2B3C4D5E6F", api.alarms.single().macAddress)
        assertEquals("Casa", api.alarms.single().name)
        assertEquals(DeviceType.ALARM, local.snapshot.single().type)
    }

    @Test
    fun `create com 409 retorna Conflict e nao altera o banco`() = runTest {
        api.videoError = httpError(409)

        val result = repository.create(DeviceInput.Video("Câmera", "ABC123", "admin", "1234"))

        assertEquals(Outcome.Failure(AppError.Conflict), result)
        assertTrue(local.snapshot.isEmpty())
    }

    @Test
    fun `update grava a resposta do servidor no banco local`() = runTest {
        api.videos += camera
        repository.refresh()

        repository.update("v1", DeviceInput.Video("Câmera 2", "XYZ", "root", "abcd"))

        assertEquals(DeviceDetails.Video("XYZ", "root", "abcd"), local.snapshot.single().details)
    }

    // ---- Remoção otimista e fila de pendências ----

    @Test
    fun `delete esconde na hora e agenda o envio mesmo sem rede`() = runTest {
        api.videos += camera
        repository.refresh()
        api.videoError = FakeDeviceApi.NETWORK_ERROR

        val result = repository.delete("v1")

        assertEquals(Outcome.Success(Unit), result)
        assertTrue(local.snapshot.isEmpty())
        assertEquals(setOf("v1"), local.pendingIds)
        assertEquals(1, scheduler.scheduled)
        assertTrue(api.deletedVideoIds.isEmpty()) // nada foi enviado ainda
    }

    @Test
    fun `delete de item inexistente retorna NotFound e nao agenda`() = runTest {
        assertEquals(Outcome.Failure(AppError.NotFound), repository.delete("nao-existe"))
        assertEquals(0, scheduler.scheduled)
    }

    @Test
    fun `envio da fila usa o endpoint do tipo do dispositivo`() = runTest {
        api.videos += camera
        api.alarms += central
        repository.refresh()
        repository.delete("a1")

        val result = repository.syncPendingOperations()

        assertEquals(PendingSyncResult.DONE, result)
        assertEquals(listOf("a1"), api.deletedAlarmIds)
        assertTrue(api.deletedVideoIds.isEmpty())
        assertFalse(local.hasRow("a1"))
        assertTrue(local.pendingIds.isEmpty())
        assertEquals(listOf("v1"), local.snapshot.map { it.id })
    }

    @Test
    fun `envio com 404 confirma a remocao`() = runTest {
        api.videos += camera
        repository.refresh()
        repository.delete("v1")
        api.videoError = httpError(404)

        assertEquals(PendingSyncResult.DONE, repository.syncPendingOperations())
        assertFalse(local.hasRow("v1"))
        assertTrue(local.pendingIds.isEmpty())
    }

    @Test
    fun `envio sem rede mantem a remocao pendente e pede nova tentativa`() = runTest {
        api.videos += camera
        repository.refresh()
        repository.delete("v1")
        api.videoError = FakeDeviceApi.NETWORK_ERROR

        assertEquals(PendingSyncResult.RETRY_LATER, repository.syncPendingOperations())
        assertTrue(local.snapshot.isEmpty()) // continua escondido
        assertEquals(setOf("v1"), local.pendingIds)
        assertEquals(1, local.failedAttempts["v1"])
    }

    @Test
    fun `envio recusado desfaz a remocao e o item volta com o favorito`() = runTest {
        api.videos += camera
        repository.refresh()
        repository.toggleFavorite("v1")
        repository.delete("v1")
        api.videoError = httpError(400)

        assertEquals(PendingSyncResult.DONE, repository.syncPendingOperations())
        val device = local.snapshot.single()
        assertEquals("v1", device.id)
        assertTrue(device.isFavorite)
        assertTrue(local.pendingIds.isEmpty())
    }

    @Test
    fun `token invalido pausa a fila sem descartar nada`() = runTest {
        api.videos += camera
        repository.refresh()
        repository.delete("v1")
        api.videoError = httpError(403)

        assertEquals(PendingSyncResult.BLOCKED, repository.syncPendingOperations())
        assertEquals(setOf("v1"), local.pendingIds)
        assertTrue(local.snapshot.isEmpty())
    }

    @Test
    fun `falha temporaria interrompe a rodada para manter a ordem`() = runTest {
        api.videos += camera
        api.alarms += central
        repository.refresh()
        repository.delete("v1") // primeira da fila
        repository.delete("a1")
        api.videoError = FakeDeviceApi.NETWORK_ERROR // só o endpoint de vídeo falha

        assertEquals(PendingSyncResult.RETRY_LATER, repository.syncPendingOperations())
        assertTrue(api.deletedAlarmIds.isEmpty()) // a1 não "furou a fila"
        assertEquals(setOf("v1", "a1"), local.pendingIds)
    }

    @Test
    fun `refresh envia as pendencias antes de buscar e o item nao volta`() = runTest {
        api.videos += camera
        repository.refresh()
        api.videoError = FakeDeviceApi.NETWORK_ERROR
        repository.delete("v1") // removido offline

        api.videoError = null // a conexão voltou
        val result = repository.refresh()

        assertEquals(Outcome.Success(Unit), result)
        assertEquals(listOf("v1"), api.deletedVideoIds)
        assertTrue(local.snapshot.isEmpty())
        assertFalse(local.hasRow("v1"))
    }

    @Test
    fun `remocao recusada avisa com o nome do dispositivo`() = runTest {
        val events = mutableListOf<SyncEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.syncEvents.collect { events += it } }
        api.videos += camera
        repository.refresh()
        repository.delete("v1")
        api.videoError = httpError(400)

        repository.syncPendingOperations()

        assertEquals(listOf(SyncEvent.DeleteRejected("Câmera")), events)
    }

    @Test
    fun `contagem de pendencias acompanha a fila`() = runTest {
        api.videos += camera
        repository.refresh()

        repository.delete("v1")
        assertEquals(1, repository.observePendingCount().first())

        repository.syncPendingOperations()
        assertEquals(0, repository.observePendingCount().first())
    }

    @Test
    fun `create envia remocoes pendentes antes e recriar com o mesmo serial funciona`() = runTest {
        api.videos += camera
        repository.refresh()
        api.videoError = FakeDeviceApi.NETWORK_ERROR
        repository.delete("v1") // removido offline; o servidor ainda tem o serial ABC123

        api.videoError = null // a conexão voltou
        val input = DeviceInput.Video(name = "Câmera nova", serial = "ABC123", username = "admin", password = "1234")
        val result = repository.create(input)

        assertTrue(result is Outcome.Success)
        assertEquals(listOf("v1"), api.deletedVideoIds) // a remoção foi antes
        assertEquals(listOf("Câmera nova"), local.snapshot.map { it.name })
    }

    @Test
    fun `sem enviar a fila o servidor recusaria o serial duplicado`() = runTest {
        api.videos += camera
        val input = DeviceInput.Video(name = "Outra", serial = "ABC123", username = "admin", password = "1234")

        assertEquals(Outcome.Failure(AppError.Conflict), repository.create(input))
    }

    @Test
    fun `dispositivo criado aparece no topo e editar nao muda a posicao`() = runTest {
        api.videos += camera
        repository.refresh()

        val created = repository.create(DeviceInput.Alarm("Casa", "AABBCCDDEEFF", "1234"))
        val alarmId = (created as Outcome.Success).data.id
        assertEquals(listOf(alarmId, "v1"), local.snapshot.map { it.id })

        repository.update(
            "v1",
            DeviceInput.Video(name = "Câmera renomeada", serial = "ABC123", username = "admin", password = "1234"),
        )
        repository.refresh()
        assertEquals(listOf(alarmId, "v1"), local.snapshot.map { it.id })
    }
}
