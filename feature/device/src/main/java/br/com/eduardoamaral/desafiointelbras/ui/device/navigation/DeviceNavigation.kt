package br.com.eduardoamaral.desafiointelbras.ui.device.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import br.com.eduardoamaral.desafiointelbras.ui.device.details.DeviceDetailsRoute
import br.com.eduardoamaral.desafiointelbras.ui.device.form.DeviceFormRoute
import kotlinx.serialization.Serializable

// Rotas type-safe (Navigation 2.8+). O Navigation guarda cada propriedade da rota no
// SavedStateHandle com o nome da propriedade como chave; os ViewModels leem por essa chave.

/** Nome da propriedade deviceId das rotas abaixo. NavArgsTest garante que continuam iguais. */
const val DEVICE_ID_ARG = "deviceId"

/** Sem id abre o cadastro; com id, a edição. */
@Serializable
data class DeviceFormDestination(val deviceId: String? = null)

@Serializable
data class DeviceDetailsDestination(val deviceId: String)

fun NavController.navigateToDeviceForm(deviceId: String? = null) = navigate(DeviceFormDestination(deviceId))

fun NavController.navigateToDeviceDetails(deviceId: String) = navigate(DeviceDetailsDestination(deviceId))

fun NavGraphBuilder.deviceFormScreen(
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
) {
    composable<DeviceFormDestination> {
        DeviceFormRoute(snackbarHostState = snackbarHostState, onBack = onBack)
    }
}

fun NavGraphBuilder.deviceDetailsScreen(onBack: () -> Unit) {
    composable<DeviceDetailsDestination> {
        DeviceDetailsRoute(onBack = onBack)
    }
}
