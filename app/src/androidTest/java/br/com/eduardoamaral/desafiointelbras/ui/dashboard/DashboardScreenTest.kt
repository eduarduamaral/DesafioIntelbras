package br.com.eduardoamaral.desafiointelbras.ui.dashboard

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.eduardoamaral.desafiointelbras.R
import br.com.eduardoamaral.desafiointelbras.domain.model.AppError
import br.com.eduardoamaral.desafiointelbras.domain.model.Device
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceDetails
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceFilter
import br.com.eduardoamaral.desafiointelbras.ui.theme.DesafioIntelbrasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import br.com.eduardoamaral.desafiointelbras.core.ui.R as CoreUiR

/**
 * Testa a tela "sem estado": recebe um DashboardUiState pronto e registra os callbacks.
 * Não precisa de ViewModel, Hilt, banco nem rede; as regras de estado já são cobertas
 * nos testes unitários, aqui o foco é o que o usuário vê e toca.
 */
@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private val camera = Device("v1", "Câmera garagem", DeviceDetails.Video("ABC123", "admin", "1234"))
    private val alarm = Device("a1", "Central escritório", DeviceDetails.Alarm("1A2B3C4D5E6F", "1234"))

    private val queries = mutableListOf<String>()
    private val filters = mutableListOf<DeviceFilter>()
    private val deleted = mutableListOf<String>()
    private var refreshes = 0

    private fun setScreen(state: DashboardUiState) {
        composeRule.setContent {
            // O campo de busca é controlado: como o ViewModel faria, o texto digitado volta no estado.
            var query by remember { mutableStateOf(state.query) }
            DesafioIntelbrasTheme {
                DashboardScreen(
                    state = state.copy(query = query),
                    snackbarHostState = SnackbarHostState(),
                    onQueryChange = {
                        queries += it
                        query = it
                    },
                    onFilterSelected = { filters += it },
                    onRefresh = { refreshes++ },
                    onAddDevice = {},
                    onEditDevice = {},
                    onOpenDetails = {},
                    onToggleFavorite = {},
                    onDeleteDevice = { deleted += it },
                )
            }
        }
    }

    private fun withDevices() = DashboardUiState(
        devices = listOf(camera, alarm),
        totalCount = 2,
        isLoadingLocal = false,
    )

    @Test
    fun mostraOsDispositivosEOTotal() {
        setScreen(withDevices())

        composeRule.onNodeWithText("Câmera garagem").assertExists()
        composeRule.onNodeWithText("Central escritório").assertExists()
        composeRule.onNodeWithText(context.resources.getQuantityString(R.plurals.devices_count, 2, 2))
            .assertExists()
    }

    @Test
    fun digitarNaBuscaEEscolherUmFiltroAvisamOViewModel() {
        setScreen(withDevices())

        composeRule.onNode(hasSetTextAction()).performTextInput("gar")
        composeRule.onNodeWithText(context.getString(R.string.filter_cameras)).performClick()

        assertEquals(listOf("gar"), queries)
        assertEquals(listOf(DeviceFilter.VIDEO), filters)
    }

    @Test
    fun removerPeloMenuPedeConfirmacaoAntesDeApagar() {
        setScreen(withDevices())

        composeRule.onNodeWithContentDescription(context.getString(R.string.device_options, camera.name))
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.action_favorite)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.action_remove)).performClick()

        // Nada é apagado só por tocar em "Remover": primeiro vem a confirmação.
        composeRule.onNodeWithText(context.getString(R.string.remove_dialog_title)).assertExists()
        assertEquals(emptyList<String>(), deleted)

        composeRule.onNodeWithText(context.getString(R.string.remove_confirm)).performClick()
        assertEquals(listOf(camera.id), deleted)
    }

    @Test
    fun listaVaziaMostraOrientacaoParaCadastrar() {
        setScreen(DashboardUiState(isLoadingLocal = false))

        composeRule.onNodeWithText(context.getString(R.string.empty_title)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.empty_message)).assertExists()
    }

    @Test
    fun erroSemDadosLocaisPermiteTentarDeNovo() {
        setScreen(DashboardUiState(isLoadingLocal = false, error = AppError.Unauthorized))

        composeRule.onNodeWithText(context.getString(R.string.error_title)).assertExists()
        composeRule.onNodeWithText(context.getString(CoreUiR.string.error_unauthorized)).assertExists()
        composeRule.onNodeWithText(context.getString(CoreUiR.string.retry)).performClick()

        assertEquals(1, refreshes)
    }

    @Test
    fun semConexaoComFilaMostraQuantasAlteracoesAguardam() {
        setScreen(withDevices().copy(isOnline = false, pendingCount = 2))

        composeRule.onNodeWithText(context.resources.getQuantityString(R.plurals.sync_offline_pending, 2, 2))
            .assertExists()
    }

    @Test
    fun itemCriadoSemConexaoMostraAguardandoEnvio() {
        setScreen(withDevices().copy(devices = listOf(camera.copy(isPendingSync = true), alarm)))

        // Só o item pendente ganha a marca.
        composeRule.onAllNodesWithText(context.getString(R.string.device_pending_sync), substring = true)
            .assertCountEquals(1)
    }
}
