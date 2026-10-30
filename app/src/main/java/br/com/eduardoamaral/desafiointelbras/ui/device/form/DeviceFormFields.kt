package br.com.eduardoamaral.desafiointelbras.ui.device.form

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import br.com.eduardoamaral.desafiointelbras.R
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceField
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceType
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceValidator
import br.com.eduardoamaral.desafiointelbras.domain.model.ValidationError
import br.com.eduardoamaral.desafiointelbras.ui.common.messageRes
import br.com.eduardoamaral.desafiointelbras.ui.components.AppTextField
import br.com.eduardoamaral.desafiointelbras.ui.components.PasswordTextField
import br.com.eduardoamaral.desafiointelbras.ui.components.labelRes

/**
 * Seletor "Alarme | Vídeo" do wireframe: trilho cinza com a opção escolhida em branco
 * e texto verde. Implementado com selectable + Role.RadioButton para o TalkBack anunciar
 * "selecionado, 1 de 2".
 */
@Composable
internal fun DeviceTypeSelector(state: DeviceFormUiState, onTypeChange: (DeviceType) -> Unit) {
    val typeError = state.errorText(DeviceField.TYPE)
    val colors = MaterialTheme.colorScheme
    Text(
        text = stringResource(R.string.form_type_label),
        style = MaterialTheme.typography.titleSmall,
        color = if (typeError != null) colors.error else colors.onSurface,
        modifier = Modifier.padding(top = 8.dp),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainerHigh)
            .padding(4.dp)
            .selectableGroup(),
    ) {
        // Ordem do wireframe: Alarme primeiro.
        listOf(DeviceType.ALARM, DeviceType.VIDEO).forEach { type ->
            val selected = state.type == type
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(MaterialTheme.shapes.small)
                    .background(if (selected) colors.background else colors.surfaceContainerHigh)
                    .selectable(
                        selected = selected,
                        // Sem ripple: a troca de fundo da opção já é o feedback visual (como no Figma).
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = state.canChangeType,
                        role = Role.RadioButton,
                        onClick = { onTypeChange(type) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(type.labelRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) colors.primary else colors.onSurface,
                )
            }
        }
    }
    if (typeError != null) {
        Text(text = typeError, style = MaterialTheme.typography.bodySmall, color = colors.error)
    }
}

@Composable
internal fun VideoFields(state: DeviceFormUiState, actions: DeviceFormActions) {
    AppTextField(
        value = state.serial,
        onValueChange = actions.onSerialChange,
        label = stringResource(R.string.form_serial_label),
        error = state.errorText(DeviceField.SERIAL),
        supportingText = stringResource(
            R.string.form_serial_number_hint_counter,
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
        supportingText = stringResource(R.string.form_alarm_password_hint),
        enabled = !state.isSaving,
    )
}

@Composable
internal fun AlarmFields(state: DeviceFormUiState, actions: DeviceFormActions) {
    AppTextField(
        value = state.macAddress,
        onValueChange = actions.onMacAddressChange,
        label = stringResource(R.string.form_mac_label),
        error = state.errorText(DeviceField.MAC_ADDRESS),
        supportingText = stringResource(
            R.string.form_mac_hint_counter,
            state.macAddress.length,
            DeviceValidator.MAC_LENGTH,
        ),
        enabled = !state.isSaving,
        capitalization = KeyboardCapitalization.Characters,
    )
    PasswordTextField(
        value = state.password,
        onValueChange = actions.onPasswordChange,
        label = stringResource(R.string.form_password_label),
        error = state.errorText(DeviceField.PASSWORD),
        supportingText = stringResource(R.string.form_alarm_password_hint),
        enabled = !state.isSaving,
        numeric = true,
    )
}

@Composable
internal fun DeviceFormUiState.errorText(field: DeviceField): String? {
    val error = errors[field] ?: return null
    return if (error == ValidationError.TOO_LONG) {
        val max = if (field == DeviceField.SERIAL) DeviceValidator.SERIAL_MAX_LENGTH else DeviceValidator.NAME_MAX_LENGTH
        stringResource(error.messageRes(), max)
    } else {
        stringResource(error.messageRes())
    }
}
