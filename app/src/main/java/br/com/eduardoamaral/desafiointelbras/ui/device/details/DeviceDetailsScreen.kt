package br.com.eduardoamaral.desafiointelbras.ui.device.details

import android.content.ClipData
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.eduardoamaral.desafiointelbras.R
import br.com.eduardoamaral.desafiointelbras.domain.model.Device
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceDetails
import br.com.eduardoamaral.desafiointelbras.ui.components.DeviceTypeIcon
import br.com.eduardoamaral.desafiointelbras.ui.components.LoadingView
import br.com.eduardoamaral.desafiointelbras.ui.components.MessageView
import br.com.eduardoamaral.desafiointelbras.ui.components.formatMac
import br.com.eduardoamaral.desafiointelbras.ui.components.kindRes
import br.com.eduardoamaral.desafiointelbras.ui.components.labelRes
import br.com.eduardoamaral.desafiointelbras.ui.theme.DesafioIntelbrasTheme
import kotlinx.coroutines.launch

@Composable
fun DeviceDetailsRoute(
    onBack: () -> Unit,
    viewModel: DeviceDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DeviceDetailsScreen(state = state, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailsScreen(state: DeviceDetailsUiState, onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (state) {
            DeviceDetailsUiState.Loading -> LoadingView(modifier)
            DeviceDetailsUiState.NotFound -> MessageView(
                icon = Icons.Outlined.SearchOff,
                title = stringResource(R.string.details_not_found),
                message = null,
                modifier = modifier,
            )
            is DeviceDetailsUiState.Success -> DeviceDetailsContent(state.device, modifier)
        }
    }
}

/**
 * Dois cards, como no wireframe: cabeçalho (ícone + nome) e dados em linhas rótulo/valor.
 * A senha não é exibida aqui (o wireframe não mostra e ela continua acessível na edição).
 */
@Composable
private fun DeviceDetailsContent(device: Device, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeviceTypeIcon(device.type)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(device.name, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(device.type.kindRes()), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        InfoCard {
            InfoRow(stringResource(R.string.details_type), stringResource(device.type.labelRes()))
            when (val details = device.details) {
                is DeviceDetails.Video -> {
                    InfoRow(stringResource(R.string.form_serial_label), details.serial, copyable = true)
                    InfoRow(stringResource(R.string.form_username_label), details.username)
                }
                is DeviceDetails.Alarm ->
                    InfoRow(stringResource(R.string.form_mac_label), details.macAddress.formatMac(), copyable = true)
            }
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
            // Mesma borda do card da lista: 1dp #F1F1F1.
            .border(1.dp, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) { content() }
}

@Composable
private fun InfoRow(label: String, value: String, copyable: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        )
        if (copyable) CopyButton(label = label, value = value)
    }
}

@Composable
private fun CopyButton(label: String, value: String) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.copied)
    IconButton(
        onClick = {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value)))
                // A partir do Android 13 o próprio sistema mostra a confirmação de cópia.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                }
            }
        },
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.ContentCopy,
            contentDescription = stringResource(R.string.copy_value, label),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun DeviceDetailsPreview() {
    DesafioIntelbrasTheme {
        DeviceDetailsScreen(
            state = DeviceDetailsUiState.Success(
                Device("1", "Câmera 1", DeviceDetails.Video("ABCDE12345678", "admin", "1234")),
            ),
            onBack = {},
        )
    }
}
