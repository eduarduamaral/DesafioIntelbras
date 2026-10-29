package br.com.eduardoamaral.desafiointelbras.data.repository

import br.com.eduardoamaral.desafiointelbras.data.remote.RetrofitDeviceRemoteDataSource
import br.com.eduardoamaral.desafiointelbras.data.remote.dto.AlarmCentralDto
import br.com.eduardoamaral.desafiointelbras.data.remote.dto.VideoDeviceDto
import br.com.eduardoamaral.desafiointelbras.domain.model.AppError
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceDetails
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceInput
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceType
import br.com.eduardoamaral.desafiointelbras.domain.model.Outcome
import br.com.eduardoamaral.desafiointelbras.testutil.FakeDeviceApi
import br.com.eduardoamaral.desafiointelbras.testutil.FakeDeviceLocalDataSource
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class OfflineFirstDeviceRepositoryTest {

    private lateinit var api: FakeDeviceApi
    private lateinit var local: FakeDeviceLocalDataSource
    private lateinit var repository: OfflineFirstDeviceRepository

    private val camera = VideoDeviceDto("v1", "Câmera", "ABC123", "admin", "1234")
    private val central = AlarmCentralDto("a1", "Central", "1A2B3C4D5E6F", "1234")

    @Before
    fun setUp() {
        api = FakeDeviceApi()
        local = FakeDeviceLocalDataSource()
        // Fonte remota real sobre a API falsa: os testes cobrem também a conversão de DTOs
        // e a tradução de erros HTTP feitas pela RetrofitDeviceRemoteDataSource.
        repository = OfflineFirstDeviceRepository(RetrofitDeviceRemoteDataSource(api), local)
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

    @Test
    fun `delete usa o endpoint do tipo do dispositivo`() = runTest {
        api.videos += camera
        api.alarms += central
        repository.refresh()

        repository.delete("a1")

        assertEquals(listOf("a1"), api.deletedAlarmIds)
        assertTrue(api.deletedVideoIds.isEmpty())
        assertEquals(listOf("v1"), local.snapshot.map { it.id })
    }

    @Test
    fun `delete com 404 remove localmente e retorna sucesso`() = runTest {
        api.videos += camera
        repository.refresh()
        api.videoError = httpError(404)

        assertEquals(Outcome.Success(Unit), repository.delete("v1"))
        assertTrue(local.snapshot.isEmpty())
    }

    @Test
    fun `delete com falha de rede mantem o dispositivo`() = runTest {
        api.videos += camera
        repository.refresh()
        api.videoError = FakeDeviceApi.NETWORK_ERROR

        assertEquals(Outcome.Failure(AppError.Network), repository.delete("v1"))
        assertEquals(1, local.snapshot.size)
    }
}
