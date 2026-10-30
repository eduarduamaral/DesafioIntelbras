package br.com.eduardoamaral.desafiointelbras.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.eduardoamaral.desafiointelbras.R
import br.com.eduardoamaral.desafiointelbras.data.network.NetworkMonitor
import br.com.eduardoamaral.desafiointelbras.domain.model.AppError
import br.com.eduardoamaral.desafiointelbras.domain.model.Device
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceFilter
import br.com.eduardoamaral.desafiointelbras.domain.model.Outcome
import br.com.eduardoamaral.desafiointelbras.domain.model.SyncEvent
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
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(DeviceFilter.ALL)
    private val isRefreshing = MutableStateFlow(false)
    private val refreshError = MutableStateFlow<AppError?>(null)

    // flatMapLatest: ao digitar, a query anterior do Room é cancelada e só a última vale.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val devices: Flow<List<Device>?> = combine(query, filter) { q, f -> q to f }
        .flatMapLatest { (q, f) -> repository.observeDevices(q, f) }

    private val totalCount: Flow<Int> = repository.observeDeviceCount()

    /**
     * Eagerly: o valor também é lido fora da UI (mensagem da remoção). Começa em true
     * para não piscar o aviso de "sem conexão" antes do primeiro callback do sistema.
     */
    private val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = true)

    // combine aceita até 5 fluxos tipados: os relacionados vão agrupados em pares.
    private val listAndTotal: Flow<Pair<List<Device>?, Int>> = combine(
        devices.onStart { emit(null) }, // null = banco ainda não respondeu
        totalCount.onStart { emit(0) },
    ) { list, total -> list to total }

    private val refreshState: Flow<Pair<Boolean, AppError?>> = combine(isRefreshing, refreshError, ::Pair)

    private val syncStatus: Flow<Pair<Boolean, Int>> = combine(
        isOnline,
        repository.observePendingCount().onStart { emit(0) },
        ::Pair,
    )

    val uiState: StateFlow<DashboardUiState> = combine(
        query,
        filter,
        listAndTotal,
        refreshState,
        syncStatus,
    ) { q, f, (list, total), (refreshing, error), (online, pending) ->
        DashboardUiState(
            query = q,
            filter = f,
            devices = list.orEmpty(),
            totalCount = total,
            isLoadingLocal = list == null,
            isRefreshing = refreshing,
            error = error,
            isOnline = online,
            pendingCount = pending,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(),
    )

    init {
        refresh()
        viewModelScope.launch {
            repository.syncEvents.collect { event ->
                when (event) {
                    is SyncEvent.DeleteRejected -> messageBus.send(
                        UserMessage(R.string.msg_delete_rejected, isError = true, formatArgs = listOf(event.deviceName)),
                    )
                }
            }
        }
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
                    // Sem rede o aviso fixo do cabeçalho já explica; o Snackbar seria repetido.
                    val explainedByBanner = result.error == AppError.Network && !isOnline.value
                    if (!explainedByBanner) messageBus.send(result.error.toUserMessage())
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
                // Some da lista nos dois casos; offline, o texto avisa que o servidor ainda não sabe.
                is Outcome.Success -> messageBus.send(
                    UserMessage(if (isOnline.value) R.string.msg_device_removed else R.string.msg_device_removed_offline),
                )
                is Outcome.Failure ->
                    messageBus.send(result.error.toUserMessage())
            }
        }
    }
}
