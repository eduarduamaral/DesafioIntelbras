package br.com.eduardoamaral.desafiointelbras.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.eduardoamaral.desafiointelbras.R
import br.com.eduardoamaral.desafiointelbras.domain.model.Device
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceDetails
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceFilter
import br.com.eduardoamaral.desafiointelbras.ui.common.clearFocusOnTap
import br.com.eduardoamaral.desafiointelbras.ui.common.messageRes
import br.com.eduardoamaral.desafiointelbras.ui.components.AppSnackbarHost
import br.com.eduardoamaral.desafiointelbras.ui.components.ConfirmDestructiveDialog
import br.com.eduardoamaral.desafiointelbras.ui.components.LoadingView
import br.com.eduardoamaral.desafiointelbras.ui.components.MessageView
import br.com.eduardoamaral.desafiointelbras.ui.dashboard.components.DeviceActionsBottomSheet
import br.com.eduardoamaral.desafiointelbras.ui.dashboard.components.DeviceFilterChips
import br.com.eduardoamaral.desafiointelbras.ui.dashboard.components.DeviceRow
import br.com.eduardoamaral.desafiointelbras.ui.dashboard.components.DeviceSearchField
import br.com.eduardoamaral.desafiointelbras.ui.dashboard.components.SyncStatusBanner
import br.com.eduardoamaral.desafiointelbras.ui.dashboard.components.itemPosition
import br.com.eduardoamaral.desafiointelbras.ui.theme.DesafioIntelbrasTheme

/** Versão "com estado": liga o ViewModel à tela. */
@Composable
fun DashboardRoute(
    snackbarHostState: SnackbarHostState,
    onAddDevice: () -> Unit,
    onEditDevice: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onQueryChange = viewModel::onQueryChange,
        onFilterSelected = viewModel::onFilterSelected,
        onRefresh = viewModel::refresh,
        onAddDevice = onAddDevice,
        onEditDevice = onEditDevice,
        onOpenDetails = onOpenDetails,
        onToggleFavorite = viewModel::onToggleFavorite,
        onDeleteDevice = viewModel::onDeleteDevice,
    )
}

/** Versão "sem estado": só desenha. Facilita Preview e teste de UI. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    snackbarHostState: SnackbarHostState,
    onQueryChange: (String) -> Unit,
    onFilterSelected: (DeviceFilter) -> Unit,
    onRefresh: () -> Unit,
    onAddDevice: () -> Unit,
    onEditDevice: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDeleteDevice: (String) -> Unit,
) {
    // Guardamos só o id; o Device vem sempre atualizado do state (ex.: favorito muda com o sheet aberto).
    var sheetDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
    val sheetDevice = state.devices.firstOrNull { it.id == sheetDeviceId }
    val deleteDevice = state.devices.firstOrNull { it.id == deleteDeviceId }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        bottomBar = { AddDeviceButton(onClick = onAddDevice) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            DashboardHeader(
                totalCount = state.totalCount,
                syncBanner = state.syncBanner,
                query = state.query,
                filter = state.filter,
                onQueryChange = onQueryChange,
                onFilterSelected = onFilterSelected,
            )

            PullToRefreshBox(
                isRefreshing = state.isRefreshing && state.devices.isNotEmpty(),
                onRefresh = onRefresh,
                // Tocar ou rolar a lista tira o foco da busca e fecha o teclado.
                modifier = Modifier
                    .fillMaxSize()
                    .clearFocusOnTap(),
            ) {
                when (val content = state.content) {
                    DashboardContent.Loading -> LoadingView()
                    DashboardContent.Devices -> DeviceList(
                        devices = state.devices,
                        onOpenDetails = onOpenDetails,
                        onMoreClick = { sheetDeviceId = it },
                    )
                    is DashboardContent.Empty -> if (content.isFiltered) {
                        MessageView(
                            icon = Icons.Outlined.SearchOff,
                            title = stringResource(R.string.empty_filtered_title),
                            message = stringResource(R.string.empty_filtered_message),
                        )
                    } else {
                        MessageView(
                            icon = Icons.Outlined.Devices,
                            title = stringResource(R.string.empty_title),
                            message = stringResource(R.string.empty_message),
                        )
                    }
                    is DashboardContent.Error -> MessageView(
                        icon = Icons.Outlined.CloudOff,
                        title = stringResource(R.string.error_title),
                        message = stringResource(content.error.messageRes()),
                        actionLabel = stringResource(R.string.retry),
                        onAction = onRefresh,
                    )
                }
            }
        }
    }

    if (sheetDevice != null) {
        DeviceActionsBottomSheet(
            device = sheetDevice,
            onDismiss = { sheetDeviceId = null },
            onInfo = {
                sheetDeviceId = null
                onOpenDetails(sheetDevice.id)
            },
            onEdit = {
                sheetDeviceId = null
                onEditDevice(sheetDevice.id)
            },
            onToggleFavorite = {
                sheetDeviceId = null
                onToggleFavorite(sheetDevice.id)
            },
            onRemove = {
                sheetDeviceId = null
                deleteDeviceId = sheetDevice.id
            },
        )
    }

    if (deleteDevice != null) {
        ConfirmDestructiveDialog(
            title = stringResource(R.string.remove_dialog_title),
            message = stringResource(R.string.remove_dialog_message),
            confirmLabel = stringResource(R.string.remove_confirm),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = {
                deleteDeviceId = null
                onDeleteDevice(deleteDevice.id)
            },
            onDismiss = { deleteDeviceId = null },
        )
    }
}

@Composable
private fun DashboardHeader(
    totalCount: Int,
    syncBanner: SyncBanner?,
    query: String,
    filter: DeviceFilter,
    onQueryChange: (String) -> Unit,
    onFilterSelected: (DeviceFilter) -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.clearFocusOnTap()) {
            Text(
                text = stringResource(R.string.dashboard_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            // Total cadastrado, independente do filtro (como no wireframe de Favoritos).
            Text(
                text = pluralStringResource(R.plurals.devices_count, totalCount, totalCount),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        SyncStatusBanner(banner = syncBanner)
        DeviceSearchField(query = query, onQueryChange = onQueryChange)
        DeviceFilterChips(
            selected = filter,
            onSelected = onFilterSelected,
            modifier = Modifier.clearFocusOnTap(),
        )
    }
}

@Composable
private fun DeviceList(
    devices: List<Device>,
    onOpenDetails: (String) -> Unit,
    onMoreClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
    ) {
        // key estável: o Compose reaproveita os itens e anima inserções e remoções.
        itemsIndexed(items = devices, key = { _, device -> device.id }) { index, device ->
            DeviceRow(
                device = device,
                position = itemPosition(index, devices.size),
                onClick = { onOpenDetails(device.id) },
                onMoreClick = { onMoreClick(device.id) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

/** "+ Adicionar" fixo no rodapé, em pílula cinza (wireframe), no lugar de um FAB. */
@Composable
private fun AddDeviceButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(48.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.add_device), style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun DashboardScreenPreview() {
    DesafioIntelbrasTheme {
        DashboardScreen(
            state = DashboardUiState(
                devices = listOf(
                    Device("1", "Central de alarme", DeviceDetails.Alarm("A0B1C2D3E4F5", "1234")),
                    Device("2", "Câmera 1", DeviceDetails.Video("ABCDE12345678", "admin", "1234"), isFavorite = true),
                    Device("3", "Câmera 2", DeviceDetails.Video("ABCDE12345679", "admin", "1234")),
                ),
                totalCount = 3,
                isLoadingLocal = false,
            ),
            snackbarHostState = SnackbarHostState(),
            onQueryChange = {},
            onFilterSelected = {},
            onRefresh = {},
            onAddDevice = {},
            onEditDevice = {},
            onOpenDetails = {},
            onToggleFavorite = {},
            onDeleteDevice = {},
        )
    }
}
