package com.tuapp.eleuthera.ui.playlists

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuapp.eleuthera.domain.model.Playlist
import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.ui.common.PlaylistCover
import com.tuapp.eleuthera.ui.common.QueueMenuButton
import com.tuapp.eleuthera.ui.common.TrackRowArt
import com.tuapp.eleuthera.ui.common.trackRowColors
import com.tuapp.eleuthera.ui.library.TrackActions
import com.tuapp.eleuthera.ui.theme.AppButton
import com.tuapp.eleuthera.ui.theme.AppCard
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PlaylistsScreen(
    modifier: Modifier = Modifier,
    actions: TrackActions = TrackActions(),
    vm: PlaylistsViewModel = hiltViewModel(),
) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val openId by vm.openId.collectAsStateWithLifecycle()
    val open = playlists.firstOrNull { it.id == openId }

    BackHandler(enabled = open != null) { vm.close() }

    if (open != null) PlaylistDetail(open, actions, vm, modifier)
    else PlaylistGrid(playlists, actions, vm, modifier)
}

// ---------- Cuadrícula de playlists ----------

@Composable
private fun PlaylistGrid(
    playlists: List<Playlist>,
    actions: TrackActions,
    vm: PlaylistsViewModel,
    modifier: Modifier,
) {
    var creating by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Playlists",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = { creating = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Nueva")
            }
        }

        if (playlists.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    "Aún no tienes playlists.\nCrea una, descarga una de YouTube\no usa el menú ⋮ de una canción.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    AppCard(onClick = { vm.open(playlist.id) }) {
                        PlaylistCover(
                            playlist.covers,
                            Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium),
                            customCover = playlist.customCover,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    playlist.name,
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${playlist.trackCount} canciones",
                                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (playlist.trackCount > 0) {
                                QueueMenuButton(
                                    loadTracks = { vm.tracksOf(playlist.id) },
                                    onPlayNext = actions.onPlayNextAll,
                                    onAddToQueue = actions.onAddAllToQueue,
                                    icon = Icons.Default.MoreVert,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("Nueva playlist") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Nombre") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = { vm.create(name); creating = false },
                ) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancelar") } },
        )
    }
}

// ---------- Detalle de una playlist ----------

@Composable
private fun PlaylistDetail(
    playlist: Playlist,
    actions: TrackActions,
    vm: PlaylistsViewModel,
    modifier: Modifier,
) {
    val tracks by vm.openTracks.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var coverMenu by remember { mutableStateOf(false) }
    // Selector de fotos del sistema: no necesita permisos.
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) vm.setCover(playlist.id, uri) }

    // Copia local: se reordena al instante mientras se arrastra y se guarda al soltar.
    var local by remember { mutableStateOf(tracks) }
    LaunchedEffect(tracks) { local = tracks }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        // Se busca por clave y no por índice, porque la cabecera también ocupa una posición.
        local = local.toMutableList().apply {
            val f = indexOfFirst { it.id == from.key }
            val t = indexOfFirst { it.id == to.key }
            if (f >= 0 && t >= 0) add(t, removeAt(f))
        }
    }

    LazyColumn(modifier.fillMaxSize(), state = listState) {
        item(key = "header") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    IconButton(onClick = vm::close) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                    Spacer(Modifier.weight(1f))
                    if (local.isNotEmpty()) {
                        QueueMenuButton(
                            loadTracks = { local },
                            onPlayNext = actions.onPlayNextAll,
                            onAddToQueue = actions.onAddAllToQueue,
                        )
                    }
                    IconButton(onClick = { renaming = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Renombrar playlist")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar playlist")
                    }
                }
                // Tocar la portada permite cambiarla por una imagen propia.
                Box(Modifier.align(Alignment.CenterHorizontally)) {
                    PlaylistCover(
                        playlist.covers,
                        Modifier
                            .size(200.dp)
                            .clip(MaterialTheme.shapes.large)
                            .clickable { coverMenu = true },
                        customCover = playlist.customCover,
                    )
                    Icon(
                        Icons.Default.PhotoCamera,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(6.dp),
                    )
                    DropdownMenu(expanded = coverMenu, onDismissRequest = { coverMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Elegir imagen") },
                            onClick = {
                                coverMenu = false
                                imagePicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                        )
                        if (playlist.customCover != null) {
                            DropdownMenuItem(
                                text = { Text("Volver al mosaico automático") },
                                onClick = { coverMenu = false; vm.clearCover(playlist.id) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${playlist.trackCount} canciones · ${formatTotal(playlist.durationMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppButton(
                        onClick = { actions.onPlay(local, 0) },
                        enabled = local.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Reproducir")
                    }
                    FilledTonalButton(
                        onClick = { actions.onShuffle(local) },
                        enabled = local.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Aleatorio")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (local.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Playlist vacía.\nAñade canciones desde Biblioteca con el menú ⋮.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
        }

        itemsIndexed(local, key = { _, track -> track.id }) { index, track ->
            ReorderableItem(reorderState, key = track.id) { dragging ->
                ListItem(
                    modifier = Modifier.clickable { actions.onPlay(local, index) },
                    colors = trackRowColors(
                        track,
                        base = if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh
                        else MaterialTheme.colorScheme.surface,
                    ),
                    leadingContent = { TrackRowArt(track) },
                    headlineContent = {
                        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PlaylistTrackMenu(
                                track = track,
                                actions = actions,
                                onRemove = { vm.remove(track) },
                            )
                            Icon(
                                Icons.Default.DragHandle,
                                contentDescription = "Arrastrar para reordenar",
                                modifier = Modifier
                                    .draggableHandle(
                                        onDragStopped = { vm.reorder(local.map { it.id }) },
                                    )
                                    .padding(12.dp),
                            )
                        }
                    },
                )
            }
        }
    }

    if (renaming) {
        var name by remember { mutableStateOf(playlist.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Renombrar playlist") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Nombre") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = { vm.rename(playlist.id, name); renaming = false },
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancelar") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar \"${playlist.name}\"") },
            text = { Text("Se borra la playlist; las canciones siguen en tu biblioteca.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(playlist.id) }) {
                    Text("Eliminar")
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

/** Menú ⋮ de una canción dentro de una playlist. */
@Composable
private fun PlaylistTrackMenu(track: Track, actions: TrackActions, onRemove: () -> Unit) {
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
                text = { Text("Añadir a otra playlist") },
                onClick = { open = false; actions.onAddToPlaylist(track) },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Quitar de esta playlist") },
                onClick = { open = false; onRemove() },
            )
        }
    }
}

private fun formatTotal(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}