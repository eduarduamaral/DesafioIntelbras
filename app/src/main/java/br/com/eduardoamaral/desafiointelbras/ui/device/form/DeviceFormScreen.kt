package br.com.eduardoamaral.desafiointelbras.ui.device.form

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.eduardoamaral.desafiointelbras.R
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceField
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceType
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceValidator
import br.com.eduardoamaral.desafiointelbras.domain.model.ValidationError
import br.com.eduardoamaral.desafiointelbras.ui.common.messageRes
import br.com.eduardoamaral.desafiointelbras.ui.components.AppSnackbarHost
import br.com.eduardoamaral.desafiointelbras.ui.components.AppTextField
import br.com.eduardoamaral.desafiointelbras.ui.components.LoadingView
import br.com.eduardoamaral.desafiointelbras.ui.components.PasswordTextField

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
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isEditing) R.string.form_title_edit else R.string.form_title_add))
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DeviceTypeSelector(state = state, onTypeChange = actions.onTypeChange)

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

            Button(
                onClick = actions.onSave,
                enabled = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.form_save))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceTypeSelector(state: DeviceFormUiState, onTypeChange: (DeviceType) -> Unit) {
    val typeError = state.errorText(DeviceField.TYPE)
    Text(
        text = stringResource(R.string.form_type_label) + " *",
        style = MaterialTheme.typography.labelLarge,
        color = if (typeError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val types = DeviceType.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        types.forEachIndexed { index, type ->
            SegmentedButton(
                selected = state.type == type,
                onClick = { onTypeChange(type) },
                shape = SegmentedButtonDefaults.itemShape(index, types.size),
                enabled = state.canChangeType,
            ) {
                Text(
                    stringResource(
                        when (type) {
                            DeviceType.VIDEO -> R.string.form_type_video
                            DeviceType.ALARM -> R.string.form_type_alarm
                        },
                    ),
                )
            }
        }
    }
    when {
        typeError != null -> Text(
            text = typeError,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        state.isEditing -> Text(
            text = stringResource(R.string.form_type_locked),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VideoFields(state: DeviceFormUiState, actions: DeviceFormActions) {
    AppTextField(
        value = state.serial,
        onValueChange = actions.onSerialChange,
        label = stringResource(R.string.form_serial_label),
        error = state.errorText(DeviceField.SERIAL),
        supportingText = stringResource(
            R.string.form_char_counter,
            state.serial.length,
            DeviceValidator.SERIAL_MAX_LENGTH,
        ),
        enabled = !state.isSaving,
        capitalization = KeyboardCapitalization.Characters,
    )
    AppTextField(
        value = state.username,
        onValueChange = actions.onUsernameChange,
        label = stringResource(R.string.form_username_label),
        error = state.errorText(DeviceField.USERNAME),
        enabled = !state.isSaving,
    )
    PasswordTextField(
        value = state.password,
        onValueChange = actions.onPasswordChange,
        label = stringResource(R.string.form_password_label),
        error = state.errorText(DeviceField.PASSWORD),
        enabled = !state.isSaving,
    )
}

@Composable
private fun AlarmFields(state: DeviceFormUiState, actions: DeviceFormActions) {
    AppTextField(
        value = state.macAddress,
        onValueChange = actions.onMacAddressChange,
        label = stringResource(R.string.form_mac_label),
        error = state.errorText(DeviceField.MAC_ADDRESS),
        supportingText = stringResource(
            R.string.form_mac_hint_counter,
            state.macAddress.length,
            DeviceFormViewModel.MAC_LENGTH,
        ),
        enabled = !state.isSaving,
        capitalization = KeyboardCapitalization.Characters,
    )
    PasswordTextField(
        value = state.password,
        onValueChange = { value -> actions.onPasswordChange(value.filter(Char::isDigit).take(6)) },
        label = stringResource(R.string.form_password_label),
        error = state.errorText(DeviceField.PASSWORD),
        supportingText = stringResource(R.string.form_alarm_password_hint),
        enabled = !state.isSaving,
        numeric = true,
    )
}

@Composable
private fun DeviceFormUiState.errorText(field: DeviceField): String? {
    val error = errors[field] ?: return null
    return if (error == ValidationError.TOO_LONG) {
        val max = if (field == DeviceField.SERIAL) DeviceValidator.SERIAL_MAX_LENGTH else DeviceValidator.NAME_MAX_LENGTH
        stringResource(error.messageRes(), max)
    } else {
        stringResource(error.messageRes())
    }
}
