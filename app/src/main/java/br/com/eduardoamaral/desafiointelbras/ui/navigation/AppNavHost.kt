package br.com.eduardoamaral.desafiointelbras.ui.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import br.com.eduardoamaral.desafiointelbras.ui.common.UserMessageBus
import br.com.eduardoamaral.desafiointelbras.ui.components.AppSnackbarVisuals
import br.com.eduardoamaral.desafiointelbras.ui.dashboard.DashboardRoute
import br.com.eduardoamaral.desafiointelbras.ui.device.details.DeviceDetailsRoute
import br.com.eduardoamaral.desafiointelbras.ui.device.form.DeviceFormRoute
import kotlinx.serialization.Serializable

// Rotas type-safe (Navigation 2.8+). O nome da propriedade "deviceId" é a chave
// que os ViewModels leem do SavedStateHandle.
@Serializable
data object DashboardDestination

@Serializable
data class DeviceFormDestination(val deviceId: String? = null)

@Serializable
data class DeviceDetailsDestination(val deviceId: String)

@Composable
fun AppNavHost(messageBus: UserMessageBus) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Um coletor só, no topo: a mensagem aparece mesmo se a tela que a gerou já saiu da pilha.
    LaunchedEffect(messageBus) {
        messageBus.messages.collect { message ->
            snackbarHostState.showSnackbar(
                AppSnackbarVisuals(
                    message = context.getString(message.textRes, *message.formatArgs.toTypedArray()),
                    isError = message.isError,
                ),
            )
        }
    }

    NavHost(navController = navController, startDestination = DashboardDestination) {
        composable<DashboardDestination> {
            DashboardRoute(
                snackbarHostState = snackbarHostState,
                onAddDevice = { navController.navigate(DeviceFormDestination()) },
                onEditDevice = { id -> navController.navigate(DeviceFormDestination(id)) },
                onOpenDetails = { id -> navController.navigate(DeviceDetailsDestination(id)) },
            )
        }
        composable<DeviceFormDestination> {
            DeviceFormRoute(
                snackbarHostState = snackbarHostState,
                onBack = { navController.popBackStack() },
            )
        }
        composable<DeviceDetailsDestination> {
            DeviceDetailsRoute(onBack = { navController.popBackStack() })
        }
    }
}
