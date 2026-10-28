package br.com.eduardoamaral.desafiointelbras.ui.device.details

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.eduardoamaral.desafiointelbras.R
import br.com.eduardoamaral.desafiointelbras.domain.model.Device
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceDetails
import br.com.eduardoamaral.desafiointelbras.ui.components.DeviceTypeIcon
import br.com.eduardoamaral.desafiointelbras.ui.components.LoadingView
import br.com.eduardoamaral.desafiointelbras.ui.components.MessageView
import br.com.eduardoamaral.desafiointelbras.ui.components.labelRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailsRoute(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: DeviceDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.details_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                actions = {
                    val current = state
                    if (current is DeviceDetailsUiState.Success) {
                        IconButton(onClick = { onEdit(current.device.id) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (val current = state) {
            DeviceDetailsUiState.Loading -> LoadingView(modifier)
            DeviceDetailsUiState.NotFound -> MessageView(
                icon = Icons.Outlined.SearchOff,
                title = stringResource(R.string.details_not_found),
                message = null,
                modifier = modifier,
            )
            is DeviceDetailsUiState.Success -> DeviceDetailsContent(current.device, modifier)
        }
    }
}

@Composable
private fun DeviceDetailsContent(device: Device, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DeviceTypeIcon(device.type)
            Spacer(Modifier.width(16.dp))
            Text(device.name, style = MaterialTheme.typography.headlineSmall)
        }
        HorizontalDivider()
        InfoRow(stringResource(R.string.details_type), stringResource(device.type.labelRes()))
        when (val details = device.details) {
            is DeviceDetails.Video -> {
                InfoRow(stringResource(R.string.form_serial_label), details.serial)
                InfoRow(stringResource(R.string.form_username_label), details.username)
            }
            is DeviceDetails.Alarm ->
                InfoRow(stringResource(R.string.form_mac_label), details.macAddress.formatMac())
        }
        PasswordInfoRow(device.details.password)
        InfoRow(
            stringResource(R.string.details_favorite),
            stringResource(if (device.isFavorite) R.string.yes else R.string.no),
        )
        InfoRow(stringResource(R.string.details_id), device.id)
    }
}

/** "1A2B3C4D5E6F" -> "1A:2B:3C:4D:5E:6F" só para exibição. */
private fun String.formatMac(): String = chunked(2).joinToString(":")

@Composable
private fun InfoRow(label: String, value: String) {
    ListItem(
        overlineContent = { Text(label) },
        headlineContent = { Text(value) },
    )
}

/** Senha oculta por padrão; o usuário revela se precisar. */
@Composable
private fun PasswordInfoRow(password: String) {
    var visible by rememberSaveable { mutableStateOf(false) }
    ListItem(
        overlineContent = { Text(stringResource(R.string.form_password_label)) },
        headlineContent = { Text(if (visible) password else "•".repeat(password.length)) },
        trailingContent = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(if (visible) R.string.password_hide else R.string.password_show),
                )
            }
        },
    )
}
