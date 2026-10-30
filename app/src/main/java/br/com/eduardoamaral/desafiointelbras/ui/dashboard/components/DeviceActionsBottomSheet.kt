package br.com.eduardoamaral.desafiointelbras.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.eduardoamaral.desafiointelbras.R
import br.com.eduardoamaral.desafiointelbras.domain.model.Device
import br.com.eduardoamaral.desafiointelbras.ui.components.kindRes

/** Menu de ações do wireframe: título com o tipo e a remoção separada por divisória, em vermelho. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceActionsBottomSheet(
    device: Device,
    onDismiss: () -> Unit,
    onInfo: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemove: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = null,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(top = 16.dp, bottom = 8.dp),
        ) {
            Text(
                text = stringResource(device.type.kindRes()),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )
            SheetAction(stringResource(R.string.action_info), onInfo) { Icon(Icons.Outlined.Info, null) }
            SheetAction(stringResource(R.string.action_edit), onEdit) { Icon(Icons.Outlined.Edit, null) }
            SheetAction(
                label = stringResource(if (device.isFavorite) R.string.action_unfavorite else R.string.action_favorite),
                onClick = onToggleFavorite,
            ) {
                if (device.isFavorite) StarOffIcon() else Icon(Icons.Outlined.StarOutline, null)
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outline,
            )
            SheetAction(
                label = stringResource(R.string.action_remove),
                onClick = onRemove,
                color = MaterialTheme.colorScheme.error,
            ) { Icon(Icons.Outlined.Delete, null) }
        }
    }
}

@Composable
private fun SheetAction(
    label: String,
    onClick: () -> Unit,
    color: Color = Color.Unspecified,
    icon: @Composable () -> Unit,
) {
    val contentColor = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        leadingContent = icon,
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = contentColor,
            leadingIconColor = contentColor,
        ),
    )
}

/** Estrela riscada ("Desmarcar favorito" no guia). O Material não tem esse ícone. */
@Composable
private fun StarOffIcon() {
    val tint = LocalContentColor.current
    Box(
        modifier = Modifier
            .size(24.dp)
            .drawWithContent {
                drawContent()
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.12f, size.height * 0.12f),
                    end = Offset(size.width * 0.88f, size.height * 0.88f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            },
    ) {
        Icon(Icons.Outlined.StarOutline, contentDescription = null, tint = tint)
    }
}
