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
}

sealed interface DashboardContent {
    data object Loading : DashboardContent
    data object Devices : DashboardContent
    data class Empty(val isFiltered: Boolean) : DashboardContent
    data class Error(val error: AppError) : DashboardContent
}
