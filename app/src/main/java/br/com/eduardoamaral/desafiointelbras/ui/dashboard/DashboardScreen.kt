package br.com.eduardoamaral.desafiointelbras.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.eduardoamaral.desafiointelbras.R
import br.com.eduardoamaral.desafiointelbras.domain.model.Device
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceFilter
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceDetails
import br.com.eduardoamaral.desafiointelbras.ui.common.messageRes
import br.com.eduardoamaral.desafiointelbras.ui.components.AppSnackbarHost
import br.com.eduardoamaral.desafiointelbras.ui.components.LoadingView
import br.com.eduardoamaral.desafiointelbras.ui.components.MessageView
import br.com.eduardoamaral.desafiointelbras.ui.dashboard.components.DeviceActionsBottomSheet
import br.com.eduardoamaral.desafiointelbras.ui.dashboard.components.DeviceCard
import br.com.eduardoamaral.desafiointelbras.ui.dashboard.components.DeviceFilterChips
import br.com.eduardoamaral.desafiointelbras.ui.dashboard.components.DeviceSearchField
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
        topBar = { TopAppBar(title = { Text(stringResource(R.string.dashboard_title)) }) },
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddDevice) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_device))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DeviceSearchField(query = state.query, onQueryChange = onQueryChange)
                DeviceFilterChips(selected = state.filter, onSelected = onFilterSelected)
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing && state.devices.isNotEmpty(),
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
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
            onEdit = {
                sheetDeviceId = null
                onEditDevice(sheetDevice.id)
            },
            onInfo = {
                sheetDeviceId = null
                onOpenDetails(sheetDevice.id)
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
        AlertDialog(
            onDismissRequest = { deleteDeviceId = null },
            title = { Text(stringResource(R.string.remove_dialog_title)) },
            text = { Text(stringResource(R.string.remove_dialog_message, deleteDevice.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteDeviceId = null
                    onDeleteDevice(deleteDevice.id)
                }) { Text(stringResource(R.string.remove_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteDeviceId = null }) { Text(stringResource(R.string.cancel)) }
            },
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // key estável: o Compose reaproveita os itens e anima reordenações.
        items(items = devices, key = { it.id }) { device ->
            DeviceCard(
                device = device,
                onClick = { onOpenDetails(device.id) },
                onMoreClick = { onMoreClick(device.id) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardScreenPreview() {
    DesafioIntelbrasTheme {
        DashboardScreen(
            state = DashboardUiState(
                devices = listOf(
                    Device(
                        id = "1",
                        name = "Câmera da garagem",
                        details = DeviceDetails.Video("ABCDEF1234567", "admin", "1234"),
                        isFavorite = true,
                    ),
                    Device("2", "Central do escritório", DeviceDetails.Alarm("1A2B3C4D5E6F", "1234")),
                ),
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
