package br.com.eduardoamaral.desafiointelbras.ui.device.form

import androidx.lifecycle.SavedStateHandle
import br.com.eduardoamaral.desafiointelbras.domain.model.AppError
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceField
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceInput
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceType
import br.com.eduardoamaral.desafiointelbras.domain.model.ValidationError
import br.com.eduardoamaral.desafiointelbras.testutil.FakeDeviceRepository
import br.com.eduardoamaral.desafiointelbras.testutil.MainDispatcherRule
import br.com.eduardoamaral.desafiointelbras.testutil.alarmDevice
import br.com.eduardoamaral.desafiointelbras.testutil.videoDevice
import br.com.eduardoamaral.desafiointelbras.ui.common.UserMessageBus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DeviceFormViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeDeviceRepository

    @Before
    fun setUp() {
        repository = FakeDeviceRepository()
    }

    private fun createViewModel(deviceId: String? = null) = DeviceFormViewModel(
        savedStateHandle = SavedStateHandle(
            if (deviceId != null) mapOf(DeviceFormViewModel.DEVICE_ID_ARG to deviceId) else emptyMap(),
        ),
        repository = repository,
        messageBus = UserMessageBus(),
    )

    @Test
    fun `novo dispositivo abre com Video selecionado`() = runTest {
        val viewModel = createViewModel()

        assertEquals(DeviceType.VIDEO, viewModel.uiState.value.type)
        assertTrue(viewModel.uiState.value.canChangeType)
    }

    @Test
    fun `salvar video incompleto mostra erros e nao chama a API`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTypeChange(DeviceType.VIDEO)
        viewModel.onNameChange("Câmera")

        viewModel.onSave()

        val errors = viewModel.uiState.value.errors
        assertEquals(setOf(DeviceField.SERIAL, DeviceField.USERNAME, DeviceField.PASSWORD), errors.keys)
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun `digitar limpa so o erro do campo editado`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTypeChange(DeviceType.VIDEO)
        viewModel.onSave()

        viewModel.onNameChange("C")

        val errors = viewModel.uiState.value.errors
        assertNull(errors[DeviceField.NAME])
        assertEquals(ValidationError.REQUIRED, errors[DeviceField.SERIAL])
    }

    @Test
    fun `MAC e normalizado enquanto o usuario digita`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTypeChange(DeviceType.ALARM)

        viewModel.onMacAddressChange("1a:2b:3c:4d:5e:6f:99")

        assertEquals("1A2B3C4D5E6F", viewModel.uiState.value.macAddress)
    }

    @Test
    fun `serial e cortado em 13 caracteres enquanto o usuario digita`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTypeChange(DeviceType.VIDEO)

        viewModel.onSerialChange("12345ksjdas98pllji")

        assertEquals("12345ksjdas98", viewModel.uiState.value.serial)
    }

    @Test
    fun `adicionar alarme valido cria e fecha a tela`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTypeChange(DeviceType.ALARM)
        viewModel.onNameChange("  Casa ")
        viewModel.onMacAddressChange("1A2B3C4D5E6F")
        viewModel.onPasswordChange("1234")

        viewModel.onSave()

        assertEquals(listOf(DeviceInput.Alarm("Casa", "1A2B3C4D5E6F", "1234")), repository.created)
        assertTrue(viewModel.uiState.value.shouldClose)
    }

    @Test
    fun `editar pre-preenche os campos do tipo e bloqueia a troca de tipo`() = runTest {
        repository.devices.value = listOf(videoDevice("42", "Garagem"))
        val viewModel = createViewModel(deviceId = "42")

        val state = viewModel.uiState.value
        assertEquals(DeviceType.VIDEO, state.type)
        assertEquals("SN42", state.serial)
        assertEquals("admin", state.username)
        assertFalse(state.canChangeType)

        viewModel.onTypeChange(DeviceType.ALARM)
        assertEquals(DeviceType.VIDEO, viewModel.uiState.value.type)
    }

    @Test
    fun `editar chama update com o id`() = runTest {
        repository.devices.value = listOf(alarmDevice("7", "Loja"))
        val viewModel = createViewModel(deviceId = "7")

        viewModel.onNameChange("Loja centro")
        viewModel.onSave()

        assertEquals(
            "7" to DeviceInput.Alarm("Loja centro", "1A2B3C4D5E6F", "1234"),
            repository.updated.single(),
        )
    }

    @Test
    fun `conflito 409 marca o campo de serial`() = runTest {
        repository.writeError = AppError.Conflict
        val viewModel = createViewModel()
        viewModel.onTypeChange(DeviceType.VIDEO)
        viewModel.onNameChange("Câmera")
        viewModel.onSerialChange("ABC123")
        viewModel.onUsernameChange("admin")
        viewModel.onPasswordChange("1234")

        viewModel.onSave()

        val state = viewModel.uiState.value
        assertEquals(ValidationError.ALREADY_EXISTS, state.errors[DeviceField.SERIAL])
        assertFalse(state.shouldClose)
        assertFalse(state.isSaving)
    }

    @Test
    fun `editar item que nao existe mais fecha a tela`() = runTest {
        val viewModel = createViewModel(deviceId = "inexistente")

        assertTrue(viewModel.uiState.value.shouldClose)
    }
}
