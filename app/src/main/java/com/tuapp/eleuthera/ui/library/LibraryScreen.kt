package com.tuapp.eleuthera.ui.library

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.ui.common.TrackArt
import kotlinx.coroutines.launch

private val audioPermission =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

/** Acciones sobre canciones; las conecta MainActivity con el reproductor y las playlists. */
data class TrackActions(
    /** Lista visible e índice pulsado: se reproduce desde ahí y el resto queda en cola. */
    val onPlay: (tracks: List<Track>, index: Int) -> Unit = { _, _ -> },
    val onPlayNext: (Track) -> Unit = {},
    val onAddToQueue: (Track) -> Unit = {},
    val onAddToPlaylist: (Track) -> Unit = {},
)

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    actions: TrackActions = TrackActions(),
    vm: LibraryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    LaunchedEffect(granted) { if (granted) vm.start() else launcher.launch(audioPermission) }

    if (!granted) {
        PermissionPrompt(modifier) { launcher.launch(audioPermission) }
        return
    }

    val state by vm.state.collectAsStateWithLifecycle()
    val open = state.openGroup
    BackHandler(enabled = open != null) { vm.closeGroup() }

    // ---- Borrado de canciones ----
    val scope = rememberCoroutineScope()
    var toDelete by remember { mutableStateOf<Track?>(null) }
    var awaitingConfirm by remember { mutableStateOf<Track?>(null) }
    val confirmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val track = awaitingConfirm
        awaitingConfirm = null
        // Android 11+ borra el archivo al aceptar; en Android 10 solo da permiso y hay que repetir.
        if (result.resultCode == Activity.RESULT_OK && track != null &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R
        ) {
            scope.launch { vm.delete(track) }
        }
    }
    val onDelete: (Track) -> Unit = { toDelete = it }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            placeholder = { Text("Buscar en tu biblioteca") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { vm.setQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Borrar búsqueda")
                    }
                }
            },
        )

        if (open != null) {
            ListItem(
                leadingContent = {
                    IconButton(onClick = vm::closeGroup) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                headlineContent = { Text(open.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text("${open.tracks.size} canciones") },
            )
            TrackList(open.tracks, actions, onDelete)
            return@Column
        }

        TabRow(selectedTabIndex = state.tab.ordinal) {
            LibraryTab.entries.forEach { tab ->
                Tab(
                    selected = tab == state.tab,
                    onClick = { vm.selectTab(tab) },
                    text = { Text(tab.label, maxLines = 1) },
                )
            }
        }

        when {
            state.loading -> Centered { CircularProgressIndicator() }
            state.empty -> Centered { Hint("Aún no hay música.\nDescarga algo desde Buscar.") }
            state.tracks.isEmpty() -> Centered { Hint("Nada coincide con \"${state.query.trim()}\".") }
            else -> when (state.tab) {
                LibraryTab.SONGS -> TrackList(state.tracks, actions, onDelete)
                LibraryTab.ARTISTS -> GroupList(state.artists, Icons.Default.Person, vm::openGroup)
                LibraryTab.ALBUMS -> GroupList(state.albums, Icons.Default.Album, vm::openGroup)
                LibraryTab.FOLDERS -> GroupList(state.folders, Icons.Default.Folder, vm::openGroup)
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
                    scope.launch {
                        val sender = vm.delete(track)
                        if (sender != null) {
                            awaitingConfirm = track
                            confirmLauncher.launch(IntentSenderRequest.Builder(sender).build())
                        }
                    }
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun TrackList(tracks: List<Track>, actions: TrackActions, onDelete: (Track) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(tracks.size, key = { tracks[it].id }) { index ->
            val track = tracks[index]
            ListItem(
                modifier = Modifier.clickable { actions.onPlay(tracks, index) },
                leadingContent = {
                    TrackArt(track.uri, Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)))
                },
                trailingContent = { TrackMenu(track, actions, onDelete) },
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
    Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PermissionPrompt(modifier: Modifier, onRequest: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Eleuthera necesita permiso para leer la música del dispositivo.",
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRequest) { Text("Conceder permiso") }
        Text(
            "Si el botón no hace nada, actívalo en Ajustes > Aplicaciones > Eleuthera > Permisos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
