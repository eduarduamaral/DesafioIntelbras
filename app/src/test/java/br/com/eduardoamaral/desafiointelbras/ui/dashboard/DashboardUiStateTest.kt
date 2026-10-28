package br.com.eduardoamaral.desafiointelbras.ui.dashboard

import br.com.eduardoamaral.desafiointelbras.domain.model.AppError
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceFilter
import br.com.eduardoamaral.desafiointelbras.testutil.videoDevice
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardUiStateTest {

    private val device = videoDevice("1", "Câmera")

    @Test
    fun `lista local tem prioridade sobre loading e erro`() {
        val state = DashboardUiState(
            devices = listOf(device),
            isLoadingLocal = false,
            isRefreshing = true,
            error = AppError.Network,
        )
        assertEquals(DashboardContent.Devices, state.content)
    }

    @Test
    fun `sem dados e sincronizando mostra loading`() {
        val state = DashboardUiState(isLoadingLocal = false, isRefreshing = true)
        assertEquals(DashboardContent.Loading, state.content)
    }

    @Test
    fun `sem dados e sem filtro mostra vazio`() {
        val state = DashboardUiState(isLoadingLocal = false)
        assertEquals(DashboardContent.Empty(isFiltered = false), state.content)
    }

    @Test
    fun `sem resultado com filtro ativo mostra vazio filtrado mesmo com erro`() {
        val state = DashboardUiState(
            filter = DeviceFilter.FAVORITES,
            isLoadingLocal = false,
            error = AppError.Timeout,
        )
        assertEquals(DashboardContent.Empty(isFiltered = true), state.content)
    }
}
