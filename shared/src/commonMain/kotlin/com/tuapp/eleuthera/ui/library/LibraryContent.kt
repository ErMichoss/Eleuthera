package com.tuapp.eleuthera.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.ui.common.QueueMenuButton
import com.tuapp.eleuthera.ui.common.SelectionBar
import com.tuapp.eleuthera.ui.common.TrackRowArt
import com.tuapp.eleuthera.ui.common.selectableRowColors
import com.tuapp.eleuthera.ui.common.trackRowClicks
import com.tuapp.eleuthera.ui.platform.PlatformBackHandler

/**
 * La biblioteca: buscador, pestañas (canciones, artistas, álbumes, carpetas), grupos,
 * selección múltiple y borrado. Igual en Android y escritorio.
 *
 * [onConfirmDelete] se llama cuando el usuario confirma que quiere borrar una canción; cómo
 * se borra depende de la plataforma. [emptyHint] es lo que se muestra sin música.
 */
@Composable
fun LibraryContent(
    controller: LibraryController,
    actions: TrackActions,
    onConfirmDelete: (Track) -> Unit,
    modifier: Modifier = Modifier,
    emptyHint: String = "Aún no hay música.\nDescarga algo desde Buscar.",
) {
    val state by controller.state.collectAsState()
    val open = state.openGroup
    PlatformBackHandler(enabled = open != null) { controller.closeGroup() }

    var toDelete by remember { mutableStateOf<Track?>(null) }
    val onDelete: (Track) -> Unit = { toDelete = it }

    // ---- Selección múltiple ----
    // Mantener pulsada una canción empieza a seleccionar; cambiar de pestaña o grupo la cancela.
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    val selecting = selected.isNotEmpty()
    LaunchedEffect(state.tab, open?.name) { selected = emptySet() }
    PlatformBackHandler(enabled = selecting) { selected = emptySet() }
    val visible = open?.tracks ?: state.tracks
    val toggle: (Track) -> Unit = { t ->
        selected = if (t.id in selected) selected - t.id else selected + t.id
    }

    Column(modifier.fillMaxSize()) {
        if (selecting) {
            SelectionBar(
                selected = visible.filter { it.id in selected },
                total = visible.size,
                onClose = { selected = emptySet() },
                onSelectAll = { selected = visible.mapTo(HashSet()) { it.id } },
                onClearAll = { selected = emptySet() },
                onPlayNext = actions.onPlayNextAll,
                onAddToQueue = actions.onAddAllToQueue,
                onAddToPlaylist = { actions.onAddAllToPlaylist(null, it) },
            )
        } else OutlinedTextField(
            value = state.query,
            onValueChange = controller::setQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            placeholder = { Text("Buscar en tu biblioteca") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { controller.setQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Borrar búsqueda")
                    }
                }
            },
        )

        if (open != null) {
            ListItem(
                leadingContent = {
                    IconButton(onClick = controller::closeGroup) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                headlineContent = { Text(open.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text("${open.tracks.size} canciones") },
                trailingContent = {
                    QueueMenuButton(
                        loadTracks = { open.tracks },
                        onPlayNext = actions.onPlayNextAll,
                        onAddToQueue = actions.onAddAllToQueue,
                        onAddToPlaylist = { actions.onAddAllToPlaylist(open.name, it) },
                    )
                },
            )
            TrackList(open.tracks, actions, onDelete, selected, toggle)
        } else {
            TabRow(selectedTabIndex = state.tab.ordinal) {
                LibraryTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == state.tab,
                        onClick = { controller.selectTab(tab) },
                        text = { Text(tab.label, maxLines = 1) },
                    )
                }
            }

            when {
                state.loading -> Centered { CircularProgressIndicator() }
                state.empty -> Centered { Hint(emptyHint) }
                state.tracks.isEmpty() -> Centered { Hint("Nada coincide con \"${state.query.trim()}\".") }
                else -> when (state.tab) {
                    LibraryTab.SONGS -> TrackList(state.tracks, actions, onDelete, selected, toggle)
                    LibraryTab.ARTISTS -> GroupList(state.artists, Icons.Default.Person, controller::openGroup)
                    LibraryTab.ALBUMS -> GroupList(state.albums, Icons.Default.Album, controller::openGroup)
                    LibraryTab.FOLDERS -> GroupList(state.folders, Icons.Default.Folder, controller::openGroup)
                }
            }
        }
    }

    toDelete?.let { track ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Eliminar canción") },
            text = {
                Text("\"${track.title}\" se borrará del dispositivo. Esta acción no se puede deshacer.")
            },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    onConfirmDelete(track)
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun TrackList(
    tracks: List<Track>,
    actions: TrackActions,
    onDelete: (Track) -> Unit,
    selected: Set<Long>,
    onToggle: (Track) -> Unit,
) {
    val selecting = selected.isNotEmpty()
    LazyColumn(Modifier.fillMaxSize()) {
        items(tracks.size, key = { tracks[it].id }) { index ->
            val track = tracks[index]
            val isSelected = track.id in selected
            ListItem(
                modifier = Modifier.trackRowClicks(
                    selecting = selecting,
                    onPlay = { actions.onPlay(tracks, index) },
                    onToggle = { onToggle(track) },
                ),
                colors = selectableRowColors(track, isSelected),
                leadingContent = { TrackRowArt(track) },
                trailingContent = {
                    // Con selección activa, una casilla en vez del menú ⋮.
                    if (selecting) Checkbox(checked = isSelected, onCheckedChange = { onToggle(track) })
                    else TrackMenu(track, actions, onDelete)
                },
                headlineContent = {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Text(
                        "${track.artist} · ${formatDuration(track.durationMs)}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun TrackMenu(track: Track, actions: TrackActions, onDelete: (Track) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Reproducir a continuación") },
                onClick = { open = false; actions.onPlayNext(track) },
            )
            DropdownMenuItem(
                text = { Text("Añadir a la cola") },
                onClick = { open = false; actions.onAddToQueue(track) },
            )
            DropdownMenuItem(
                text = { Text("Añadir a playlist") },
                onClick = { open = false; actions.onAddToPlaylist(track) },
            )
            actions.onTrim?.let { onTrim ->
                DropdownMenuItem(
                    text = { Text("Recortar contenido") },
                    onClick = { open = false; onTrim(track) },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Eliminar del dispositivo", color = MaterialTheme.colorScheme.error) },
                onClick = { open = false; onDelete(track) },
            )
        }
    }
}

@Composable
private fun GroupList(
    groups: List<LibraryGroup>,
    icon: ImageVector,
    onOpen: (LibraryGroup) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(groups, key = { it.name }) { group ->
            ListItem(
                modifier = Modifier.clickable { onOpen(group) },
                leadingContent = { Icon(icon, contentDescription = null) },
                headlineContent = {
                    Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Text(group.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            )
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) { content() }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
    )
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}