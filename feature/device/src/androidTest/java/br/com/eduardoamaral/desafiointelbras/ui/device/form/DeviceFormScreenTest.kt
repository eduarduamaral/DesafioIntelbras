package br.com.eduardoamaral.desafiointelbras.ui.device.form

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceField
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceType
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceValidator
import br.com.eduardoamaral.desafiointelbras.domain.model.ValidationError
import br.com.eduardoamaral.desafiointelbras.feature.device.R
import br.com.eduardoamaral.desafiointelbras.ui.theme.DesafioIntelbrasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import br.com.eduardoamaral.desafiointelbras.core.ui.R as CoreUiR

@RunWith(AndroidJUnit4::class)
class DeviceFormScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private val types = mutableListOf<DeviceType>()
    private var saves = 0

    private fun setScreen(state: DeviceFormUiState) {
        composeRule.setContent {
            DesafioIntelbrasTheme {
                DeviceFormScreen(
                    state = state,
                    snackbarHostState = SnackbarHostState(),
                    actions = DeviceFormActions(
                        onTypeChange = { types += it },
                        onSave = { saves++ },
                    ),
                )
            }
        }
    }

    @Test
    fun cadastroDeVideoMostraOsCamposDoTipoEPermiteTrocarParaAlarme() {
        setScreen(DeviceFormUiState(type = DeviceType.VIDEO))

        composeRule.onNodeWithText(context.getString(R.string.form_title_add)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.form_serial_label)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.form_username_label)).assertExists()

        composeRule.onNodeWithText(context.getString(CoreUiR.string.form_type_alarm)).performClick()
        assertEquals(listOf(DeviceType.ALARM), types)
    }

    @Test
    fun errosDeValidacaoAparecemEmCadaCampo() {
        setScreen(
            DeviceFormUiState(
                type = DeviceType.VIDEO,
                errors = mapOf(
                    DeviceField.NAME to ValidationError.REQUIRED,
                    DeviceField.SERIAL to ValidationError.TOO_LONG,
                ),
            ),
        )

        composeRule.onNodeWithText(context.getString(CoreUiR.string.form_error_required)).assertExists()
        composeRule.onNodeWithText(
            context.getString(CoreUiR.string.form_error_too_long, DeviceValidator.SERIAL_MAX_LENGTH),
        ).assertExists()
    }

    @Test
    fun edicaoEscondeOTipoESalvarAvisaOViewModel() {
        setScreen(DeviceFormUiState(isEditing = true, type = DeviceType.ALARM, name = "Casa"))

        composeRule.onNodeWithText(context.getString(R.string.form_title_edit)).assertExists()
        // Vídeo e alarme são recursos diferentes na API: na edição o tipo não pode mudar.
        composeRule.onNodeWithText(context.getString(R.string.form_type_label)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.form_mac_label)).assertExists()

        composeRule.onNodeWithText(context.getString(R.string.form_save)).performClick()
        assertEquals(1, saves)
    }
}
