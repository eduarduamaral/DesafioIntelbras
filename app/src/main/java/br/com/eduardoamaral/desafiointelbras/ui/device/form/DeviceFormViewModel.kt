package br.com.eduardoamaral.desafiointelbras.ui.device.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.eduardoamaral.desafiointelbras.R
import br.com.eduardoamaral.desafiointelbras.domain.model.AppError
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceDetails
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceField
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceInput
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceType
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceValidator
import br.com.eduardoamaral.desafiointelbras.domain.model.Outcome
import br.com.eduardoamaral.desafiointelbras.domain.model.ValidationError
import br.com.eduardoamaral.desafiointelbras.domain.repository.DeviceRepository
import br.com.eduardoamaral.desafiointelbras.ui.common.UserMessage
import br.com.eduardoamaral.desafiointelbras.ui.common.UserMessageBus
import br.com.eduardoamaral.desafiointelbras.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceFormUiState(
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    /** null até o usuário escolher. Na edição vem preenchido e não pode mudar (são recursos diferentes na API). */
    val type: DeviceType? = null,
    val name: String = "",
    val serial: String = "",
    val username: String = "",
    val macAddress: String = "",
    val password: String = "",
    val errors: Map<DeviceField, ValidationError> = emptyMap(),
    val isSaving: Boolean = false,
    /** Vira true após salvar com sucesso (ou se o item sumiu); a tela observa e volta. */
    val shouldClose: Boolean = false,
) {
    val canChangeType: Boolean get() = !isEditing && !isSaving
}

@HiltViewModel
class DeviceFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DeviceRepository,
    private val messageBus: UserMessageBus,
) : ViewModel() {

    // Lemos a chave crua (mesmo nome da propriedade em DeviceFormDestination) em vez de toRoute(),
    // para o ViewModel continuar testável sem Robolectric.
    private val deviceId: String? = savedStateHandle[DEVICE_ID_ARG]

    private val _uiState = MutableStateFlow(DeviceFormUiState(isEditing = deviceId != null))
    val uiState: StateFlow<DeviceFormUiState> = _uiState.asStateFlow()

    init {
        if (deviceId != null) loadDevice(deviceId)
    }

    /** Pré-preenche a partir do banco local (fonte única de verdade). */
    private fun loadDevice(id: String) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val device = repository.observeDevice(id).first()
            if (device == null) {
                // Removido em outra tela/sincronização enquanto o form abria.
                messageBus.send(UserMessage(R.string.error_not_found, isError = true))
                _uiState.update { it.copy(isLoading = false, shouldClose = true) } // fecha a tela
                return@launch
            }
            _uiState.update { state ->
                when (val details = device.details) {
                    is DeviceDetails.Video -> state.copy(
                        type = DeviceType.VIDEO,
                        name = device.name,
                        serial = details.serial,
                        username = details.username,
                        password = details.password,
                    )
                    is DeviceDetails.Alarm -> state.copy(
                        type = DeviceType.ALARM,
                        name = device.name,
                        macAddress = details.macAddress,
                        password = details.password,
                    )
                }.copy(isLoading = false)
            }
        }
    }

    fun onTypeChange(value: DeviceType) {
        if (!_uiState.value.canChangeType) return
        // A senha tem regras diferentes por tipo; limpamos para não carregar uma inválida.
        _uiState.update {
            it.copy(type = value, password = "", errors = it.errors - DeviceField.TYPE - DeviceField.PASSWORD)
        }
    }

    fun onNameChange(value: String) = updateField(DeviceField.NAME) { copy(name = value) }
    fun onSerialChange(value: String) = updateField(DeviceField.SERIAL) { copy(serial = value) }
    fun onUsernameChange(value: String) = updateField(DeviceField.USERNAME) { copy(username = value) }
    fun onPasswordChange(value: String) = updateField(DeviceField.PASSWORD) { copy(password = value) }

    fun onMacAddressChange(value: String) = updateField(DeviceField.MAC_ADDRESS) {
        // Normaliza enquanto digita: só hexadecimal, maiúsculo, no máximo 12 caracteres.
        copy(macAddress = DeviceValidator.normalizeMac(value).filter { it in HEX }.take(MAC_LENGTH))
    }

    /** Altera um campo e limpa o erro dele (o usuário já está corrigindo). */
    private inline fun updateField(field: DeviceField, crossinline change: DeviceFormUiState.() -> DeviceFormUiState) {
        _uiState.update { it.change().copy(errors = it.errors - field) }
    }

    fun onSave() {
        val current = _uiState.value
        if (current.isSaving) return

        val input = current.toInput()
        if (input == null) {
            _uiState.update { it.copy(errors = it.errors + (DeviceField.TYPE to ValidationError.REQUIRED)) }
            return
        }
        val errors = DeviceValidator.validate(input)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(errors = errors) }
            return
        }

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = if (deviceId == null) {
                repository.create(input)
            } else {
                repository.update(deviceId, input)
            }
            when (result) {
                is Outcome.Success -> {
                    messageBus.send(
                        UserMessage(if (deviceId == null) R.string.msg_device_created else R.string.msg_device_updated),
                    )
                    _uiState.update { it.copy(isSaving = false, shouldClose = true) }
                }
                is Outcome.Failure -> {
                    messageBus.send(result.error.toUserMessage())
                    _uiState.update {
                        it.copy(isSaving = false, errors = it.errors + conflictFieldError(input, result.error))
                    }
                }
            }
        }
    }

    /** 409 significa serial (vídeo) ou MAC (alarme) duplicado: marcamos o campo culpado. */
    private fun conflictFieldError(input: DeviceInput, error: AppError): Map<DeviceField, ValidationError> {
        if (error != AppError.Conflict) return emptyMap()
        val field = when (input) {
            is DeviceInput.Video -> DeviceField.SERIAL
            is DeviceInput.Alarm -> DeviceField.MAC_ADDRESS
        }
        return mapOf(field to ValidationError.ALREADY_EXISTS)
    }

    private fun DeviceFormUiState.toInput(): DeviceInput? = when (type) {
        DeviceType.VIDEO -> DeviceInput.Video(
            name = name.trim(),
            serial = serial.trim(),
            username = username.trim(),
            password = password,
        )
        DeviceType.ALARM -> DeviceInput.Alarm(name = name.trim(), macAddress = macAddress, password = password)
        null -> null
    }

    companion object {
        const val DEVICE_ID_ARG = "deviceId"
        const val MAC_LENGTH = 12
        private const val HEX = "0123456789ABCDEF"
    }
}
