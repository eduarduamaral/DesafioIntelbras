package br.com.eduardoamaral.desafiointelbras.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.eduardoamaral.desafiointelbras.R
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceType

@Composable
fun DeviceTypeIcon(type: DeviceType, modifier: Modifier = Modifier) {
    val (icon, description) = when (type) {
        DeviceType.VIDEO -> Icons.Outlined.Videocam to R.string.device_type_video
        DeviceType.ALARM -> Icons.Outlined.Security to R.string.device_type_alarm
    }
    Surface(
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(description),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

fun DeviceType.labelRes(): Int = when (this) {
    DeviceType.VIDEO -> R.string.device_type_video
    DeviceType.ALARM -> R.string.device_type_alarm
}
