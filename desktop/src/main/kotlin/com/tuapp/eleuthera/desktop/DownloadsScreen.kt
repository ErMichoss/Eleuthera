package com.tuapp.eleuthera.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tuapp.eleuthera.data.download.DesktopDownloads
import com.tuapp.eleuthera.domain.model.DownloadStatus
import com.tuapp.eleuthera.platform.Tools

/**
 * Cola de descargas, a la derecha de Buscar. En Android su progreso va en las
 * notificaciones; en escritorio se ve aquí.
 */
@Composable
fun DownloadQueuePanel(modifier: Modifier = Modifier) {
    val downloads = AppGraph.downloads
    val items by downloads.items.collectAsState()
    val active = items.count { it.status is DownloadStatus.Queued || it.status is DownloadStatus.Running }

    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (active > 0) "Descargas ($active)" else "Descargas",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (active > 0) TextButton(onClick = downloads::cancelAll) { Text("Cancelar todo") }
            if (items.size > active) TextButton(onClick = downloads::clearFinished) { Text("Limpiar") }
        }
        if (items.isEmpty()) {
            Text(
                "Lo que descargues aparecerá aquí y después en la biblioteca.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        LazyColumn {
            items(items, key = { it.track.id }) { item -> DownloadRow(item, downloads) }
        }
    }
}

@Composable
private fun DownloadRow(item: DesktopDownloads.Item, downloads: DesktopDownloads) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(item.track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.playlist?.let {
                Text(
                    it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            when (val s = item.status) {
                DownloadStatus.Queued -> Text("En cola", style = MaterialTheme.typography.labelSmall)
                is DownloadStatus.Running ->
                    // 100 % = yt-dlp ya bajó el audio y ffmpeg lo está convirtiendo.
                    if (s.percent >= 100) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { s.percent / 100f }, modifier = Modifier.fillMaxWidth())
                DownloadStatus.Done -> Text(
                    "Descargada", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                is DownloadStatus.Failed -> Text(
                    s.message, style = MaterialTheme.typography.labelSmall, maxLines = 3,
                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (item.status is DownloadStatus.Failed) {
            IconButton(onClick = { downloads.retry(item.track.id) }) { Icon(Icons.Default.Refresh, "Reintentar") }
        }
        IconButton(onClick = { downloads.cancel(item.track.id) }) {
            Icon(Icons.Default.Close, if (item.status is DownloadStatus.Done) "Quitar" else "Cancelar")
        }
    }
}

/**
 * Aviso mientras se instalan yt-dlp, ffmpeg y Deno, o si algo falló. No ocupa sitio si
 * todo está listo.
 */
@Composable
fun ToolsBanner() {
    val tools by Tools.state.collectAsState()
    val working = tools.working
    val error = tools.error
    when {
        working != null -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(working, style = MaterialTheme.typography.bodySmall)
            val p = tools.progress
            if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        error != null -> Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            TextButton(onClick = AppGraph::prepareTools) { Text("Reintentar") }
        }
    }
}