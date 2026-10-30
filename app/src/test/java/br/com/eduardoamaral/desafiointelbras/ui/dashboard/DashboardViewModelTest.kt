package br.com.eduardoamaral.desafiointelbras.ui.dashboard

import br.com.eduardoamaral.desafiointelbras.R
import br.com.eduardoamaral.desafiointelbras.domain.model.AppError
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceFilter
import br.com.eduardoamaral.desafiointelbras.testutil.FakeDeviceRepository
import br.com.eduardoamaral.desafiointelbras.testutil.MainDispatcherRule
import br.com.eduardoamaral.desafiointelbras.testutil.alarmDevice
import br.com.eduardoamaral.desafiointelbras.testutil.networkFailure
import br.com.eduardoamaral.desafiointelbras.testutil.videoDevice
import br.com.eduardoamaral.desafiointelbras.ui.common.UserMessage
import br.com.eduardoamaral.desafiointelbras.ui.common.UserMessageBus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeDeviceRepository
    private lateinit var messageBus: UserMessageBus

    private val camera = videoDevice("1", "Câmera garagem")
    private val alarm = alarmDevice("2", "Central escritório", isFavorite = true)

    @Before
    fun setUp() {
        repository = FakeDeviceRepository().apply { devices.value = listOf(camera, alarm) }
        messageBus = UserMessageBus()
    }

    /** Cria o ViewModel e mantém o uiState sendo coletado (WhileSubscribed precisa de um assinante). */
    private fun TestScope.createViewModel(): DashboardViewModel {
        val viewModel = DashboardViewModel(repository, messageBus)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        return viewModel
    }

    @Test
    fun `ao iniciar sincroniza com a API e mostra a lista local`() = runTest {
        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertEquals(listOf(alarm, camera), state.devices) // ordenado por nome
        assertEquals(DashboardContent.Devices, state.content)
        assertEquals(1, repository.refreshCount)
    }

    @Test
    fun `filtro de cameras mostra apenas dispositivos de video`() = runTest {
        val viewModel = createViewModel()

        viewModel.onFilterSelected(DeviceFilter.VIDEO)

        assertEquals(listOf(camera), viewModel.uiState.value.devices)
    }

    @Test
    fun `filtro de favoritos mostra apenas favoritos`() = runTest {
        val viewModel = createViewModel()

        viewModel.onFilterSelected(DeviceFilter.FAVORITES)

        assertEquals(listOf(alarm), viewModel.uiState.value.devices)
    }

    @Test
    fun `busca filtra pelo nome ignorando maiusculas`() = runTest {
        val viewModel = createViewModel()

        viewModel.onQueryChange("GARAGEM")

        assertEquals(listOf(camera), viewModel.uiState.value.devices)
    }

    @Test
    fun `erro no refresh emite snackbar e mantem a lista local`() = runTest {
        repository.refreshResult = networkFailure
        val messages = mutableListOf<UserMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            messageBus.messages.collect { messages += it }
        }

        val viewModel = createViewModel()

        assertEquals(UserMessage(R.string.error_network, isError = true), messages.single())
        assertEquals(2, viewModel.uiState.value.devices.size)
        assertEquals(DashboardContent.Devices, viewModel.uiState.value.content)
    }

    @Test
    fun `erro no refresh sem dados locais mostra estado de erro`() = runTest {
        repository.devices.value = emptyList()
        repository.refreshResult = networkFailure

        val viewModel = createViewModel()

        assertEquals(DashboardContent.Error(AppError.Network), viewModel.uiState.value.content)
    }

    @Test
    fun `total do cabecalho ignora filtro e busca`() = runTest {
        val viewModel = createViewModel()
        assertEquals(2, viewModel.uiState.value.totalCount)

        viewModel.onFilterSelected(DeviceFilter.FAVORITES)
        viewModel.onQueryChange("central")

        val state = viewModel.uiState.value
        assertEquals(listOf(alarm), state.devices)
        assertEquals(2, state.totalCount)
    }

    @Test
    fun `total do cabecalho acompanha a remocao`() = runTest {
        val viewModel = createViewModel()

        viewModel.onDeleteDevice(camera.id)

        assertEquals(1, viewModel.uiState.value.totalCount)
    }

    @Test
    fun `alternar favorito atualiza a lista`() = runTest {
        val viewModel = createViewModel()

        viewModel.onToggleFavorite(camera.id)

        assertTrue(viewModel.uiState.value.devices.first { it.id == camera.id }.isFavorite)
    }

    @Test
    fun `remover com sucesso tira da lista e avisa o usuario`() = runTest {
        val messages = mutableListOf<UserMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            messageBus.messages.collect { messages += it }
        }
        val viewModel = createViewModel()

        viewModel.onDeleteDevice(camera.id)

        assertEquals(listOf(alarm), viewModel.uiState.value.devices)
        assertEquals(UserMessage(R.string.msg_device_removed), messages.single())
    }
}
