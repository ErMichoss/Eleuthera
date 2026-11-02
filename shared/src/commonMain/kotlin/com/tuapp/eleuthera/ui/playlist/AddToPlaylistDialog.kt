package com.tuapp.eleuthera.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.ui.platform.LocalToast
import kotlinx.coroutines.launch

/**
 * Qué se quiere añadir a una playlist: una canción, una lista entera (álbum, artista,
 * carpeta, otra playlist) o una selección. [label] es su nombre, que se propone para una
 * playlist nueva; null en una selección suelta, que no tiene nombre.
 */
data class PlaylistTarget(val label: String?, val tracks: List<Track>) {
    constructor(track: Track) : this(track.title, listOf(track))
}

/** Elegir una playlist existente para [target], o crear una nueva con sus canciones dentro. */
@Composable
fun AddToPlaylistDialog(target: PlaylistTarget, vm: PlaylistsController, onDismiss: () -> Unit) {
    val playlists by vm.playlists.collectAsState()
    var newName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    val many = target.tracks.size > 1
    // Nombre propuesto para una playlist nueva: el del álbum/playlist que se añade entero.
    val suggested = target.label?.takeIf { many }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir a playlist") },
        text = {
            Column {
                Text(
                    target.label ?: "Canciones seleccionadas",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (many) {
                    Text(
                        "${target.tracks.size} canciones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        ListItem(
                            modifier = Modifier.clickable {
                                scope.launch {
                                    val added = vm.addAll(playlist.id, target.tracks)
                                    toast(resultMessage(added, target.tracks.size, playlist.name))
                                    onDismiss()
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(playlist.name) },
                            supportingContent = { Text("${playlist.trackCount} canciones") },
                        )
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("Nueva playlist") },
                    // Al añadir un álbum o playlist entera, se propone su nombre.
                    placeholder = if (suggested != null) {
                        { Text(suggested) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            val name = newName.ifBlank { suggested.orEmpty() }
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    vm.create(name, target.tracks)
                    onDismiss()
                },
            ) { Text("Crear y añadir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun resultMessage(added: Int, total: Int, playlist: String): String = when {
    total == 1 && added == 1 -> "Añadida a $playlist"
    total == 1 -> "Ya estaba en $playlist"
    added == total -> "$added canciones añadidas a $playlist"
    added == 0 -> "Todas ya estaban en $playlist"
    else -> "$added canciones añadidas a $playlist (${total - added} ya estaban)"
}
/**
 * Elegir a qué playlist irá algo que aún no está descargado (Buscar > "Descargar en
 * playlist…"): devuelve el nombre de una existente o de una nueva, que se crea al terminar.
 * [subject] es lo que se va a añadir (el título del vídeo o de la playlist de YouTube).
 */
@Composable
fun ChoosePlaylistDialog(
    subject: String,
    vm: PlaylistsController,
    onPick: (playlistName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val playlists by vm.playlists.collectAsState()
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Descargar en playlist") },
        text = {
            Column {
                Text(subject, maxLines = 2, overflow = TextOverflow.Ellipsis)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        ListItem(
                            modifier = Modifier.clickable {
                                onPick(playlist.name)
                                onDismiss()
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(playlist.name) },
                            supportingContent = { Text("${playlist.trackCount} canciones") },
                        )
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("Nueva playlist") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = newName.isNotBlank(),
                onClick = {
                    onPick(newName.trim())
                    onDismiss()
                },
            ) { Text("Crear y descargar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}