package br.com.eduardoamaral.desafiointelbras.ui.dashboard

import br.com.eduardoamaral.desafiointelbras.domain.model.AppError
import br.com.eduardoamaral.desafiointelbras.domain.model.Device
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceFilter

data class DashboardUiState(
    val query: String = "",
    val filter: DeviceFilter = DeviceFilter.ALL,
    val devices: List<Device> = emptyList(),
    /** Total cadastrado, sem filtro nem busca ("3 dispositivos" no cabeçalho). */
    val totalCount: Int = 0,
    val isLoadingLocal: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: AppError? = null,
    val isOnline: Boolean = true,
    /** Alterações feitas offline que ainda não chegaram ao servidor. */
    val pendingCount: Int = 0,
) {
    private val isFiltering: Boolean get() = query.isNotBlank() || filter != DeviceFilter.ALL

    /**
     * Uma única regra para decidir o que mostrar. Fica aqui (e não no Composable)
     * para ser testada em teste unitário.
     */
    val content: DashboardContent
        get() = when {
            devices.isNotEmpty() -> DashboardContent.Devices
            isLoadingLocal || isRefreshing -> DashboardContent.Loading
            error != null && !isFiltering -> DashboardContent.Error(error)
            else -> DashboardContent.Empty(isFiltered = isFiltering)
        }

    /** Aviso fixo do cabeçalho. null = online e sem nada na fila. */
    val syncBanner: SyncBanner?
        get() = when {
            !isOnline && pendingCount > 0 -> SyncBanner.OfflineWithPending(pendingCount)
            !isOnline -> SyncBanner.Offline
            pendingCount > 0 -> SyncBanner.Syncing(pendingCount)
            else -> null
        }
}

sealed interface SyncBanner {
    data object Offline : SyncBanner
    data class OfflineWithPending(val count: Int) : SyncBanner
    data class Syncing(val count: Int) : SyncBanner
}

sealed interface DashboardContent {
    data object Loading : DashboardContent
    data object Devices : DashboardContent
    data class Empty(val isFiltered: Boolean) : DashboardContent
    data class Error(val error: AppError) : DashboardContent
}
