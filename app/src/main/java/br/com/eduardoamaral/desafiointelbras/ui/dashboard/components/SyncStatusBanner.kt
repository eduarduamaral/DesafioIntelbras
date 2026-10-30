package br.com.eduardoamaral.desafiointelbras.ui.dashboard.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.eduardoamaral.desafiointelbras.R
import br.com.eduardoamaral.desafiointelbras.ui.dashboard.SyncBanner
import br.com.eduardoamaral.desafiointelbras.ui.theme.DesafioIntelbrasTheme

/**
 * Aviso discreto sobre conexão e fila de envio. Fica no cabeçalho (e não num Snackbar)
 * porque é um estado que dura, não um evento.
 * liveRegion: o TalkBack anuncia quando o texto muda (ex.: a conexão caiu).
 */
@Composable
fun SyncStatusBanner(banner: SyncBanner?, modifier: Modifier = Modifier) {
    // Guarda o último aviso para a animação de saída ainda ter o que desenhar.
    var lastBanner by remember { mutableStateOf(banner) }
    if (banner != null) lastBanner = banner

    AnimatedVisibility(
        visible = banner != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        val current = lastBanner ?: return@AnimatedVisibility
        val (icon, text) = when (current) {
            SyncBanner.Offline -> Icons.Outlined.CloudOff to stringResource(R.string.sync_offline)
            is SyncBanner.OfflineWithPending -> Icons.Outlined.CloudOff to
                pluralStringResource(R.plurals.sync_offline_pending, current.count, current.count)
            is SyncBanner.Syncing -> Icons.Outlined.Sync to
                pluralStringResource(R.plurals.sync_pending, current.count, current.count)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SyncStatusBannerPreview() {
    DesafioIntelbrasTheme {
        SyncStatusBanner(SyncBanner.OfflineWithPending(2), Modifier.padding(16.dp))
    }
}
