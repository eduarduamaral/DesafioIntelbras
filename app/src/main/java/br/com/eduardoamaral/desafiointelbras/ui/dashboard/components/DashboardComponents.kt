package br.com.eduardoamaral.desafiointelbras.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.eduardoamaral.desafiointelbras.R
import br.com.eduardoamaral.desafiointelbras.domain.model.Device
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceDetails
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceFilter
import br.com.eduardoamaral.desafiointelbras.ui.components.DeviceTypeIcon
import br.com.eduardoamaral.desafiointelbras.ui.components.formatMac
import br.com.eduardoamaral.desafiointelbras.ui.components.kindRes

/**
 * Campo de busca: pílula com lupa e botão de limpar quando há texto.
 * Desvio consciente do guia: no foco a borda fica verde, como nos campos do formulário,
 * porque a borda cinza sozinha não indicava o foco. Ver "Decisões" no README.
 */
@Composable
fun DeviceSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, CircleShape)
                    .border(1.dp, if (focused) colors.primary else colors.outlineVariant, CircleShape)
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    // Guia (Iconografia): ícone ativo em #00B26B.
                    tint = if (focused) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Filled.Cancel,
                            contentDescription = stringResource(R.string.search_clear),
                            tint = colors.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        },
    )
}

@Composable
fun DeviceFilterChips(
    selected: DeviceFilter,
    onSelected: (DeviceFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DeviceFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelected(filter) },
                label = { Text(stringResource(filter.labelRes()), style = MaterialTheme.typography.labelMedium) },
                shape = CircleShape,
                border = null,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colors.surfaceContainerHigh,
                    labelColor = colors.onSurface,
                    selectedContainerColor = colors.primaryContainer,
                    selectedLabelColor = colors.primary,
                ),
            )
        }
    }
}

private fun DeviceFilter.labelRes(): Int = when (this) {
    DeviceFilter.ALL -> R.string.filter_all
    DeviceFilter.FAVORITES -> R.string.filter_favorites
    DeviceFilter.VIDEO -> R.string.filter_cameras
    DeviceFilter.ALARM -> R.string.filter_alarms
}

/** Posição do item dentro do card da lista: define cantos arredondados e divisória. */
enum class ItemPosition { SINGLE, FIRST, MIDDLE, LAST }

fun itemPosition(index: Int, count: Int): ItemPosition = when {
    count == 1 -> ItemPosition.SINGLE
    index == 0 -> ItemPosition.FIRST
    index == count - 1 -> ItemPosition.LAST
    else -> ItemPosition.MIDDLE
}

private fun ItemPosition.shape(radius: Int = 12): Shape = when (this) {
    ItemPosition.SINGLE -> RoundedCornerShape(radius.dp)
    ItemPosition.FIRST -> RoundedCornerShape(topStart = radius.dp, topEnd = radius.dp)
    ItemPosition.LAST -> RoundedCornerShape(bottomStart = radius.dp, bottomEnd = radius.dp)
    ItemPosition.MIDDLE -> RectangleShape
}

/**
 * Borda de 1dp (#F1F1F1) em volta do card inteiro, não de cada item.
 * Cada item desenha um retângulo arredondado "esticado" para fora dos seus limites nos lados
 * em que a lista continua; o recorte (clipRect) deixa aparecer só as bordas externas.
 * Juntos, os itens formam um contorno contínuo, sem linhas duplicadas entre eles.
 */
private fun Modifier.groupBorder(
    position: ItemPosition,
    color: Color,
    width: Dp = 1.dp,
    radius: Dp = 12.dp,
): Modifier = drawWithContent {
    drawContent()
    val stroke = width.toPx()
    val r = radius.toPx()
    val continuesAbove = position == ItemPosition.MIDDLE || position == ItemPosition.LAST
    val continuesBelow = position == ItemPosition.FIRST || position == ItemPosition.MIDDLE
    val top = if (continuesAbove) -(r + stroke) else stroke / 2
    val bottom = if (continuesBelow) size.height + r + stroke else size.height - stroke / 2
    clipRect {
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2, top),
            size = Size(size.width - stroke, bottom - top),
            cornerRadius = CornerRadius(r, r),
            style = Stroke(width = stroke),
        )
    }
}

/** Texto de apoio da lista: serial para câmera, MAC formatado para alarme. */
fun Device.subtitle(): String = when (val d = details) {
    is DeviceDetails.Video -> d.serial
    is DeviceDetails.Alarm -> d.macAddress.formatMac()
}

/**
 * Uma linha da lista. Os itens desenham juntos um único card (fundo #FAFAFA com cantos
 * arredondados só nas pontas e divisórias entre eles), como no wireframe. Cada linha
 * continua sendo um item separado da LazyColumn, então a lista segue eficiente.
 */
@Composable
fun DeviceRow(
    device: Device,
    position: ItemPosition,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = position.shape()
    Column(
        modifier = modifier
            .fillMaxWidth()
            // A borda vem antes do clip para não ser cortada nos cantos arredondados.
            .groupBorder(position, MaterialTheme.colorScheme.surfaceContainerHigh)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        if (position == ItemPosition.MIDDLE || position == ItemPosition.LAST) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DeviceTypeIcon(type = device.type)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = device.subtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onMoreClick) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.device_options, device.name),
                )
            }
        }
    }
}

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
