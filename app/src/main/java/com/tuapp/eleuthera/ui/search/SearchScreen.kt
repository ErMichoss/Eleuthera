package com.tuapp.eleuthera.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuapp.eleuthera.domain.model.DownloadStatus
import com.tuapp.eleuthera.domain.model.RemoteTrack
import com.tuapp.eleuthera.ui.importer.ImportPanel

@Composable
fun SearchScreen(modifier: Modifier = Modifier, vm: SearchViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            singleLine = true,
            placeholder = { Text("Buscar o pegar un enlace") },
            trailingIcon = {
                IconButton(onClick = { keyboard?.hide(); vm.submit() }) {
                    Icon(Icons.Default.Search, contentDescription = "Buscar")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); vm.submit() }),
        )

        ImportPanel()

        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        state.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
        }

        // Enlace de playlist: botón para descargarla entera.
        state.playlistTitle?.let { title ->
            ListItem(
                headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text("${state.results.size} canciones") },
                trailingContent = {
                    FilledTonalButton(onClick = vm::downloadAll) { Text("Descargar todo") }
                },
            )
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(state.results, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    status = state.downloads[track.id],
                    onDownload = { vm.download(track) },
                    onCancel = { vm.cancel(track) },
                )
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: RemoteTrack,
    status: DownloadStatus?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    val failed = status is DownloadStatus.Failed
    ListItem(
        headlineContent = { Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val detail = when (status) {
                DownloadStatus.Queued -> "En cola…"
                is DownloadStatus.Running -> "Descargando… ${status.percent} %"
                DownloadStatus.Done -> "Guardado en Música/Eleuthera"
                is DownloadStatus.Failed -> status.message
                null -> listOf(track.author, formatDuration(track.durationSec))
                    .filter { it.isNotEmpty() }.joinToString(" · ")
            }
            Text(
                text = detail,
                maxLines = if (failed) 6 else 2,
                overflow = TextOverflow.Ellipsis,
                color = if (failed) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            when (status) {
                DownloadStatus.Queued -> IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "Cancelar")
                }
                is DownloadStatus.Running -> IconButton(onClick = onCancel) {
                    CircularProgressIndicator(
                        progress = { status.percent / 100f },
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 3.dp,
                    )
                }
                DownloadStatus.Done ->
                    Icon(Icons.Default.CheckCircle, contentDescription = "Descargado")
                is DownloadStatus.Failed -> IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reintentar")
                }
                null -> IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = "Descargar")
                }
            }
        },
    )
}

private fun formatDuration(seconds: Long): String =
    if (seconds <= 0) "" else "%d:%02d".format(seconds / 60, seconds % 60)