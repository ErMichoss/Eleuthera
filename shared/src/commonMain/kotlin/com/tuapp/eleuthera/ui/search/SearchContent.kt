package com.tuapp.eleuthera.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tuapp.eleuthera.domain.model.DownloadStatus
import com.tuapp.eleuthera.domain.model.RemoteTrack
import com.tuapp.eleuthera.ui.library.TrackActions
import com.tuapp.eleuthera.ui.platform.LocalRemoteImage
import com.tuapp.eleuthera.ui.platform.LocalToast
import com.tuapp.eleuthera.ui.playlists.ChoosePlaylistDialog
import com.tuapp.eleuthera.ui.playlists.PlaylistsController

/**
 * Buscar en YouTube o pegar un enlace, y descargar. Igual en Android y escritorio.
 * [header] va debajo del buscador (en Android, el panel de importar playlists).
 *
 * El menú ⋮ de cada resultado permite descargar y, al terminar, reproducir, mandar a la cola
 * ([actions]) o añadir a una playlist ([playlists]), sin tener que buscarla luego en la
 * biblioteca.
 */
@Composable
fun SearchContent(
    vm: SearchController,
    modifier: Modifier = Modifier,
    actions: TrackActions = TrackActions(),
    playlists: PlaylistsController? = null,
    header: @Composable () -> Unit = {},
) {
    val state by vm.state.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val toast = LocalToast.current

    // Canciones recién descargadas con una acción pendiente, y avisos del controlador.
    LaunchedEffect(vm) {
        vm.ready.collect { (action, track) ->
            when (action) {
                AfterDownload.PLAY -> actions.onPlay(listOf(track), 0)
                AfterDownload.PLAY_NEXT -> actions.onPlayNext(track)
                AfterDownload.ADD_TO_QUEUE -> actions.onAddToQueue(track)
            }
        }
    }
    LaunchedEffect(vm) { vm.messages.collect { toast(it) } }

    // Diálogo "Descargar en playlist…": qué se va a añadir y qué hacer con el nombre elegido.
    var choosing by remember { mutableStateOf<Pair<String, (String) -> Unit>?>(null) }
    if (playlists != null) {
        choosing?.let { (subject, onPick) ->
            ChoosePlaylistDialog(subject, playlists, onPick = onPick, onDismiss = { choosing = null })
        }
    }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQueryChange,
            // Enter en un teclado físico (escritorio, o tablet con teclado) también busca.
            modifier = Modifier.fillMaxWidth().padding(16.dp).onPreviewKeyEvent {
                if (it.key == Key.Enter && it.type == KeyEventType.KeyDown) {
                    vm.submit()
                    true
                } else false
            },
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

        header()

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(onClick = vm::downloadAll) { Text("Descargar todo") }
                        if (playlists != null) {
                            var menu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
                                }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Descargar todo en otra playlist…") },
                                        onClick = {
                                            menu = false
                                            choosing = "$title (${state.results.size} canciones)" to { name: String -> vm.downloadAllInto(name) }
                                        },
                                    )
                                }
                            }
                        }
                    }
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
                    onThen = { vm.downloadThen(track, it) },
                    onIntoPlaylist = if (playlists != null) {
                        { choosing = track.title to { name: String -> vm.downloadInto(track, name) } }
                    } else null,
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
    onThen: (AfterDownload) -> Unit,
    onIntoPlaylist: (() -> Unit)?,
) {
    val failed = status is DownloadStatus.Failed
    ListItem(
        headlineContent = { Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            LocalRemoteImage.current(
                track.thumbnailUrl,
                Modifier
                    .size(width = 72.dp, height = 40.dp) // Proporción 16:9 típica de YouTube
                    .clip(RoundedCornerShape(4.dp)),
            )
        },
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
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                ThenMenu(done = status == DownloadStatus.Done, onThen = onThen, onIntoPlaylist = onIntoPlaylist)
            }
        },
    )
}

/**
 * Menú ⋮ de un resultado. Si aún no está descargado: descargar y, al terminar, reproducir /
 * a continuación / a la cola / a una playlist. Si ya lo está, lo mismo pero al momento.
 */
@Composable
private fun ThenMenu(done: Boolean, onThen: (AfterDownload) -> Unit, onIntoPlaylist: (() -> Unit)?) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AfterDownload.entries.forEach { action ->
                DropdownMenuItem(
                    text = { Text(if (done) action.label else "Descargar y ${action.label.lowercase()}") },
                    onClick = { open = false; onThen(action) },
                )
            }
            if (onIntoPlaylist != null) {
                DropdownMenuItem(
                    text = { Text(if (done) "Añadir a playlist…" else "Descargar en playlist…") },
                    onClick = { open = false; onIntoPlaylist() },
                )
            }
        }
    }
}

private fun formatDuration(seconds: Long): String =
    if (seconds <= 0) "" else "%d:%02d".format(seconds / 60, seconds % 60)