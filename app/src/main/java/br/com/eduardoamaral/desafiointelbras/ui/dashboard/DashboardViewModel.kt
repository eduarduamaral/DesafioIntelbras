package br.com.eduardoamaral.desafiointelbras.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.eduardoamaral.desafiointelbras.R
import br.com.eduardoamaral.desafiointelbras.domain.model.AppError
import br.com.eduardoamaral.desafiointelbras.domain.model.Device
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceFilter
import br.com.eduardoamaral.desafiointelbras.domain.model.Outcome
import br.com.eduardoamaral.desafiointelbras.domain.repository.DeviceRepository
import br.com.eduardoamaral.desafiointelbras.ui.common.UserMessage
import br.com.eduardoamaral.desafiointelbras.ui.common.UserMessageBus
import br.com.eduardoamaral.desafiointelbras.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: DeviceRepository,
    private val messageBus: UserMessageBus,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(DeviceFilter.ALL)
    private val isRefreshing = MutableStateFlow(false)
    private val refreshError = MutableStateFlow<AppError?>(null)

    // flatMapLatest: ao digitar, a query anterior do Room é cancelada e só a última vale.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val devices: Flow<List<Device>?> = combine(query, filter) { q, f -> q to f }
        .flatMapLatest { (q, f) -> repository.observeDevices(q, f) }

    val uiState: StateFlow<DashboardUiState> = combine(
        query,
        filter,
        devices.onStart { emit(null) }, // null = banco ainda não respondeu
        isRefreshing,
        refreshError,
    ) { q, f, list, refreshing, error ->
        DashboardUiState(
            query = q,
            filter = f,
            devices = list.orEmpty(),
            isLoadingLocal = list == null,
            isRefreshing = refreshing,
            error = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(),
    )

    init {
        refresh()
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onFilterSelected(value: DeviceFilter) {
        filter.value = value
    }

    fun refresh() {
        if (isRefreshing.value) return
        isRefreshing.value = true
        viewModelScope.launch {
            when (val result = repository.refresh()) {
                is Outcome.Success -> refreshError.value = null
                is Outcome.Failure -> {
                    refreshError.value = result.error
                    messageBus.send(result.error.toUserMessage())
                }
            }
            isRefreshing.value = false
        }
    }

    fun onToggleFavorite(id: String) {
        viewModelScope.launch { repository.toggleFavorite(id) }
    }

    fun onDeleteDevice(id: String) {
        viewModelScope.launch {
            when (val result = repository.delete(id)) {
                is Outcome.Success -> messageBus.send(UserMessage(R.string.msg_device_removed))
                is Outcome.Failure ->
                    messageBus.send(result.error.toUserMessage())
            }
        }
    }
}
