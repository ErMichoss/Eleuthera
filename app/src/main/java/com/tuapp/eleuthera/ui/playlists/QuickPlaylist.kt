package com.tuapp.eleuthera.ui.playlists

import androidx.compose.animation.core.animate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tuapp.eleuthera.domain.model.Playlist
import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.ui.common.PlaylistCover
import com.tuapp.eleuthera.ui.common.QueueMenuButton
import com.tuapp.eleuthera.ui.library.TrackActions
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Capa por encima de la pantalla con el botón de playlists rápidas, que se puede arrastrar.
 * Al soltarlo se pega al borde izquierdo o derecho y se guarda su posición.
 * La capa no intercepta toques: solo el botón los recibe.
 */
@Composable
fun QuickPlaylistsOverlay(
    playlists: List<Playlist>,
    actions: TrackActions,
    loadTracks: suspend (playlistId: Long) -> List<Track>,
    savedPosition: Pair<Float, Float>?,
    onPositionChange: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val margin = with(density) { 16.dp.toPx() }
        val size = with(density) { FAB_SIZE.toPx() }
        val minX = margin
        val maxX = (constraints.maxWidth - size - margin).coerceAtLeast(minX)
        val minY = margin
        val maxY = (constraints.maxHeight - size - margin).coerceAtLeast(minY)

        // Posición en píxeles; por defecto, abajo a la derecha.
        var x by remember { mutableFloatStateOf(maxX) }
        var y by remember { mutableFloatStateOf(maxY) }
        var dragging by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        // Coloca el botón donde se dejó (también si cambia el tamaño, p. ej. al girar el móvil).
        LaunchedEffect(savedPosition, maxX, maxY) {
            if (dragging) return@LaunchedEffect
            val (fx, fy) = savedPosition ?: (1f to 1f)
            x = minX + fx * (maxX - minX)
            y = minY + fy * (maxY - minY)
        }

        fun release() {
            dragging = false
            // Se pega al borde más cercano y se guarda en fracciones del área.
            val target = if (x + size / 2 < constraints.maxWidth / 2f) minX else maxX
            scope.launch {
                animate(x, target) { value, _ -> x = value }
                onPositionChange(
                    if (maxX > minX) (target - minX) / (maxX - minX) else 1f,
                    if (maxY > minY) (y - minY) / (maxY - minY) else 1f,
                )
            }
        }

        QuickPlaylistsButton(
            playlists = playlists,
            actions = actions,
            loadTracks = loadTracks,
            modifier = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .pointerInput(minX, maxX, minY, maxY) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { release() },
                        onDragCancel = { release() },
                    ) { change, drag ->
                        change.consume()
                        x = (x + drag.x).coerceIn(minX, maxX)
                        y = (y + drag.y).coerceIn(minY, maxY)
                    }
                },
        )
    }
}

/** Tamaño del SmallFloatingActionButton de Material 3. */
private val FAB_SIZE = 40.dp

/**
 * Playlists rápidas (opcional, Ajustes > Biblioteca): botón flotante que abre una hoja con
 * todas tus playlists. Desde ahí se reproducen, se barajan o se mandan a la cola sin
 * salir de la pantalla en la que estés.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPlaylistsButton(
    playlists: List<Playlist>,
    actions: TrackActions,
    loadTracks: suspend (playlistId: Long) -> List<Track>,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }

    SmallFloatingActionButton(onClick = { open = true }, modifier = modifier) {
        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = "Playlists rápidas")
    }

    if (!open) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()

    /** Carga la playlist, hace [action] con sus canciones y cierra la hoja. */
    fun playAndClose(playlistId: Long, action: (List<Track>) -> Unit) {
        scope.launch {
            val tracks = loadTracks(playlistId)
            if (tracks.isNotEmpty()) action(tracks)
            sheetState.hide()
            open = false
        }
    }

    ModalBottomSheet(onDismissRequest = { open = false }, sheetState = sheetState) {
        Text(
            "Tus playlists",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (playlists.isEmpty()) {
            Text(
                "Aún no tienes playlists.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp).navigationBarsPadding(),
            )
            return@ModalBottomSheet
        }
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
            items(playlists, key = { it.id }) { playlist ->
                val playable = playlist.trackCount > 0
                ListItem(
                    modifier = Modifier.clickable(enabled = playable) {
                        playAndClose(playlist.id) { actions.onPlay(it, 0) }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    leadingContent = {
                        PlaylistCover(
                            playlist.covers,
                            Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                            customCover = playlist.customCover,
                        )
                    },
                    headlineContent = {
                        Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = { Text("${playlist.trackCount} canciones") },
                    trailingContent = {
                        if (playable) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    playAndClose(playlist.id) { actions.onShuffle(it) }
                                }) {
                                    Icon(Icons.Default.Shuffle, contentDescription = "Aleatorio")
                                }
                                QueueMenuButton(
                                    loadTracks = { loadTracks(playlist.id) },
                                    onPlayNext = actions.onPlayNextAll,
                                    onAddToQueue = actions.onAddAllToQueue,
                                    onAddToPlaylist = { actions.onAddAllToPlaylist(playlist.name, it) },
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}