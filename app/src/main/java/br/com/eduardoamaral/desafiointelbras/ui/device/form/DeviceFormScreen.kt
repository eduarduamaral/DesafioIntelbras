package br.com.eduardoamaral.desafiointelbras.ui.device.form

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.eduardoamaral.desafiointelbras.R
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceField
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceType
import br.com.eduardoamaral.desafiointelbras.ui.components.AppSnackbarHost
import br.com.eduardoamaral.desafiointelbras.ui.components.AppTextField
import br.com.eduardoamaral.desafiointelbras.ui.components.LoadingView
import br.com.eduardoamaral.desafiointelbras.ui.theme.DesafioIntelbrasTheme

@Composable
fun DeviceFormRoute(
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    viewModel: DeviceFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.shouldClose) {
        if (state.shouldClose) onBack()
    }

    DeviceFormScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        actions = DeviceFormActions(
            onBack = onBack,
            onTypeChange = viewModel::onTypeChange,
            onNameChange = viewModel::onNameChange,
            onSerialChange = viewModel::onSerialChange,
            onUsernameChange = viewModel::onUsernameChange,
            onMacAddressChange = viewModel::onMacAddressChange,
            onPasswordChange = viewModel::onPasswordChange,
            onSave = viewModel::onSave,
        ),
    )
}

/** Agrupa os callbacks para a assinatura do Composable não ficar enorme. */
data class DeviceFormActions(
    val onBack: () -> Unit = {},
    val onTypeChange: (DeviceType) -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onSerialChange: (String) -> Unit = {},
    val onUsernameChange: (String) -> Unit = {},
    val onMacAddressChange: (String) -> Unit = {},
    val onPasswordChange: (String) -> Unit = {},
    val onSave: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceFormScreen(
    state: DeviceFormUiState,
    snackbarHostState: SnackbarHostState,
    actions: DeviceFormActions,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isEditing) R.string.form_title_edit else R.string.form_title_add))
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                    }
                },
                // "Salvar" como ação de texto no topo, como no wireframe.
                actions = {
                    if (state.isSaving) {
                        Box(Modifier.padding(horizontal = 20.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        TextButton(onClick = actions.onSave, enabled = !state.isLoading) {
                            Text(stringResource(R.string.form_save), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            LoadingView(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Na edição o tipo não aparece: vídeo e alarme são recursos diferentes na API.
            if (!state.isEditing) {
                DeviceTypeSelector(state = state, onTypeChange = actions.onTypeChange)
            }

            AppTextField(
                value = state.name,
                onValueChange = actions.onNameChange,
                label = stringResource(R.string.form_name_label),
                error = state.errorText(DeviceField.NAME),
                enabled = !state.isSaving,
                capitalization = KeyboardCapitalization.Sentences,
            )

            // Os campos mudam conforme o tipo; a animação deixa a troca menos brusca.
            AnimatedContent(targetState = state.type, label = "deviceTypeFields") { type ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (type) {
                        DeviceType.VIDEO -> VideoFields(state, actions)
                        DeviceType.ALARM -> AlarmFields(state, actions)
                        null -> Unit
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun DeviceFormScreenPreview() {
    DesafioIntelbrasTheme {
        DeviceFormScreen(
            state = DeviceFormUiState(type = DeviceType.VIDEO),
            snackbarHostState = SnackbarHostState(),
            actions = DeviceFormActions(),
        )
    }
}
