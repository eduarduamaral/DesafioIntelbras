package br.com.eduardoamaral.desafiointelbras.ui.dashboard.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import br.com.eduardoamaral.desafiointelbras.ui.dashboard.DashboardRoute
import kotlinx.serialization.Serializable

// A feature expõe a rota e o pedaço do grafo; o :app só decide para onde cada clique leva.

@Serializable
data object DashboardDestination

fun NavGraphBuilder.dashboardScreen(
    snackbarHostState: SnackbarHostState,
    onAddDevice: () -> Unit,
    onEditDevice: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
) {
    composable<DashboardDestination> {
        DashboardRoute(
            snackbarHostState = snackbarHostState,
            onAddDevice = onAddDevice,
            onEditDevice = onEditDevice,
            onOpenDetails = onOpenDetails,
        )
    }
}
