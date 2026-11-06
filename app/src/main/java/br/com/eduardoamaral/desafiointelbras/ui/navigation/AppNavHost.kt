package br.com.eduardoamaral.desafiointelbras.ui.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import br.com.eduardoamaral.desafiointelbras.ui.common.UserMessageBus
import br.com.eduardoamaral.desafiointelbras.ui.components.AppSnackbarVisuals
import br.com.eduardoamaral.desafiointelbras.ui.dashboard.navigation.DashboardDestination
import br.com.eduardoamaral.desafiointelbras.ui.dashboard.navigation.dashboardScreen
import br.com.eduardoamaral.desafiointelbras.ui.device.navigation.deviceDetailsScreen
import br.com.eduardoamaral.desafiointelbras.ui.device.navigation.deviceFormScreen
import br.com.eduardoamaral.desafiointelbras.ui.device.navigation.navigateToDeviceDetails
import br.com.eduardoamaral.desafiointelbras.ui.device.navigation.navigateToDeviceForm

@Composable
fun AppNavHost(messageBus: UserMessageBus) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    // LocalResources (e não LocalContext) para o texto acompanhar mudanças de idioma/configuração.
    // rememberUpdatedState: o coletor continua o mesmo, mas sempre lê o Resources atual.
    val resources by rememberUpdatedState(LocalResources.current)

    // Um coletor só, no topo: a mensagem aparece mesmo se a tela que a gerou já saiu da pilha.
    LaunchedEffect(messageBus) {
        messageBus.messages.collect { message ->
            snackbarHostState.showSnackbar(
                AppSnackbarVisuals(
                    message = resources.getString(message.textRes, *message.formatArgs.toTypedArray()),
                    isError = message.isError,
                ),
            )
        }
    }

    // As features não se conhecem: a lista só avisa "abrir formulário/detalhes" e o app
    // traduz isso em navegação para as rotas da feature de dispositivo.
    NavHost(navController = navController, startDestination = DashboardDestination) {
        dashboardScreen(
            snackbarHostState = snackbarHostState,
            onAddDevice = { navController.navigateToDeviceForm() },
            onEditDevice = { id -> navController.navigateToDeviceForm(id) },
            onOpenDetails = { id -> navController.navigateToDeviceDetails(id) },
        )
        deviceFormScreen(
            snackbarHostState = snackbarHostState,
            onBack = { navController.popBackStack() },
        )
        deviceDetailsScreen(onBack = { navController.popBackStack() })
    }
}
