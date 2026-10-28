package br.com.eduardoamaral.desafiointelbras.ui.device.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.eduardoamaral.desafiointelbras.domain.model.Device
import br.com.eduardoamaral.desafiointelbras.domain.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface DeviceDetailsUiState {
    data object Loading : DeviceDetailsUiState
    data object NotFound : DeviceDetailsUiState
    data class Success(val device: Device) : DeviceDetailsUiState
}

@HiltViewModel
class DeviceDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: DeviceRepository,
) : ViewModel() {

    private val deviceId: String = checkNotNull(savedStateHandle[DEVICE_ID_ARG])

    val uiState: StateFlow<DeviceDetailsUiState> = repository.observeDevice(deviceId)
        .map { device ->
            if (device == null) DeviceDetailsUiState.NotFound else DeviceDetailsUiState.Success(device)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeviceDetailsUiState.Loading)

    companion object {
        const val DEVICE_ID_ARG = "deviceId"
    }
}
