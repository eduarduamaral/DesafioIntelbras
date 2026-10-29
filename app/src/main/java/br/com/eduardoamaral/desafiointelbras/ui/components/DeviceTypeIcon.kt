package br.com.eduardoamaral.desafiointelbras.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.eduardoamaral.desafiointelbras.R
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceType

/**
 * Ícone do tipo dentro de uma caixa só com borda, como no wireframe (lista e informações).
 * Sem fundo próprio: a caixa fica com a mesma cor da célula onde está.
 */
@Composable
fun DeviceTypeIcon(type: DeviceType, modifier: Modifier = Modifier) {
    // Ícones exportados do Style Guide do Figma (res/drawable). O tint segue o tema.
    val icon = when (type) {
        DeviceType.VIDEO -> R.drawable.ic_device_camera
        DeviceType.ALARM -> R.drawable.ic_device_alarm
    }
    Box(
        modifier = modifier
            .size(40.dp)
            .border(1.dp, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small), // #F1F1F1
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(type.descriptionRes()),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Descrição longa, para leitores de tela. */
@StringRes
fun DeviceType.descriptionRes(): Int = when (this) {
    DeviceType.VIDEO -> R.string.device_type_video
    DeviceType.ALARM -> R.string.device_type_alarm
}

/** "Câmera" / "Alarme": título do menu de ações. */
@StringRes
fun DeviceType.kindRes(): Int = when (this) {
    DeviceType.VIDEO -> R.string.device_kind_camera
    DeviceType.ALARM -> R.string.device_kind_alarm
}

/** "Vídeo" / "Alarme": seletor do formulário e linha "Tipo" das informações. */
@StringRes
fun DeviceType.labelRes(): Int = when (this) {
    DeviceType.VIDEO -> R.string.form_type_video
    DeviceType.ALARM -> R.string.form_type_alarm
}

/** "1A2B3C4D5E6F" -> "1A:2B:3C:4D:5E:6F". A API guarda sem separador; exibimos como no wireframe. */
fun String.formatMac(): String = chunked(2).joinToString(":")
