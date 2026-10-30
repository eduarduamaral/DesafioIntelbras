package br.com.eduardoamaral.desafiointelbras.ui.dashboard.components

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.eduardoamaral.desafiointelbras.R
import br.com.eduardoamaral.desafiointelbras.domain.model.DeviceFilter

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
