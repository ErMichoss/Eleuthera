package com.tuapp.eleuthera.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tuapp.eleuthera.data.download.DesktopDownloads
import com.tuapp.eleuthera.domain.model.AudioFormat
import com.tuapp.eleuthera.domain.model.DownloadStatus
import com.tuapp.eleuthera.domain.model.RemoteTrack
import com.tuapp.eleuthera.platform.Tools
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/**
 * Descargar: buscar en YouTube o pegar un enlace (vídeo o playlist) y la cola de descargas.
 * Es provisional; la pantalla de búsqueda de Android llega en la fase 5.
 */
@Composable
fun DownloadsScreen(modifier: Modifier) {
    val downloads = AppGraph.downloads
    val items by downloads.items.collectAsState()
    val tools by Tools.state.collectAsState()
    var text by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<RemoteTrack>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val isUrl = looksLikeUrl(text)

    fun submit() {
        val input = text.trim()
        if (input.isEmpty() || busy || tools.ytDlp == null) return
        busy = true
        message = null
        scope.launch {
            try {
                if (looksLikeUrl(input)) {
                    val n = downloads.enqueueUrl(input)
                    message = if (n == 1) "Canción añadida a la cola" else "$n canciones añadidas a la cola"
                    text = ""
                } else {
                    results = AppGraph.ytDlp.search(input)
                    if (results.isEmpty()) message = "Sin resultados"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Error: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    Row(modifier) {
        Column(Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("Busca en YouTube o pega un enlace de vídeo o playlist") },
                    leadingIcon = { Icon(if (isUrl) Icons.Default.Download else Icons.Default.Search, null) },
                    modifier = Modifier.weight(1f).onPreviewKeyEvent {
                        if (it.key == Key.Enter && it.type == KeyEventType.KeyDown) {
                            submit()
                            true
                        } else false
                    },
                )
                Button(onClick = ::submit, enabled = !busy && text.isNotBlank() && tools.ytDlp != null) {
                    Text(if (isUrl) "Descargar" else "Buscar")
                }
            }
            FormatChooser()
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
            message?.let {
                Text(
                    it,
                    color = if (it.startsWith("Error")) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(results, key = { it.id }) { r ->
                    val status = items.firstOrNull { it.track.id == r.id }?.status
                    ResultRow(r, status) { downloads.enqueue(r) }
                }
            }
        }
        VerticalDivider()
        DownloadQueue(items, downloads, Modifier.width(380.dp).fillMaxHeight())
    }
}

/** MP3 o M4A, el mismo ajuste que en Android. */
@Composable
private fun FormatChooser() {
    val settings = AppGraph.downloadSettings
    val format by settings.format.collectAsState(AudioFormat.MP3)
    val scope = rememberCoroutineScope()
    Row(
        Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Formato:", style = MaterialTheme.typography.bodyMedium)
        AudioFormat.entries.forEach { f ->
            FilterChip(
                selected = format == f,
                onClick = { scope.launch { settings.setFormat(f) } },
                label = { Text(f.label) },
            )
        }
    }
}

@Composable
private fun ResultRow(track: RemoteTrack, status: DownloadStatus?, onDownload: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    track.author.ifEmpty { null },
                    track.durationSec.takeIf { it > 0 }?.let { "%d:%02d".format(it / 60, it % 60) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (status) {
            is DownloadStatus.Running -> Text("${status.percent}%", style = MaterialTheme.typography.labelMedium)
            DownloadStatus.Queued -> Text("En cola", style = MaterialTheme.typography.labelMedium)
            DownloadStatus.Done -> Icon(Icons.Default.CheckCircle, "Descargada", tint = MaterialTheme.colorScheme.primary)
            else -> IconButton(onClick = onDownload) { Icon(Icons.Default.Download, "Descargar") }
        }
    }
}

@Composable
private fun DownloadQueue(items: List<DesktopDownloads.Item>, downloads: DesktopDownloads, modifier: Modifier) {
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

private fun looksLikeUrl(text: String): Boolean {
    val t = text.trim().lowercase()
    return t.startsWith("http://") || t.startsWith("https://") ||
            "youtube.com/" in t || "youtu.be/" in t
}