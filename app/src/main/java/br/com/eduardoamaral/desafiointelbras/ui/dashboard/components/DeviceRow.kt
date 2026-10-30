package br.com.eduardoamaral.desafiointelbras.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.eduardoamaral.desafiointelbras.R
import br.com.eduardoamaral.desafiointelbras.domain.model.Device
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceDetails
import br.com.eduardoamaral.desafiointelbras.ui.components.DeviceTypeIcon
import br.com.eduardoamaral.desafiointelbras.ui.components.formatMac

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
