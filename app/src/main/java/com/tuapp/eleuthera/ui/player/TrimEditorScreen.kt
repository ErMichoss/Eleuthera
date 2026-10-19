package com.tuapp.eleuthera.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.ui.common.TrackArt
import kotlinx.coroutines.delay

@Composable
fun TrimEditorScreen(
    track: Track,
    onClose: (Track?) -> Unit,
    modifier: Modifier = Modifier,
    vm: TrimEditorViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val skin by vm.progressSkin.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var previewing by remember(track.id) { mutableStateOf(false) }
    var playheadMs by remember(track.id) { mutableLongStateOf(0L) }
    var skipPending by remember { mutableStateOf(false) }
    var playFromSelectedSegment by remember { mutableStateOf(false) }
    val currentPlayFromSelectedSegment by rememberUpdatedState(playFromSelectedSegment)
    var confirmReplace by remember { mutableStateOf(false) }
    val preview = remember(track.uri) { ExoPlayer.Builder(context).build() }
    val hasAudioAfterEdit =
        state.mode != TrimMode.REMOVE_SELECTED ||
            state.startMs > 0 || state.endMs < state.durationMs

    LaunchedEffect(track.id) { vm.load(track) }
    DisposableEffect(preview, track.uri) {
        preview.setMediaItem(MediaItem.fromUri(track.uri))
        preview.prepare()
        onDispose { preview.release() }
    }
    LaunchedEffect(preview, state.mode) {
        skipPending = false
        preview.repeatMode =
            if (state.mode == TrimMode.REMOVE_SELECTED) Player.REPEAT_MODE_ONE
            else Player.REPEAT_MODE_OFF
    }
    LaunchedEffect(previewing, state.startMs, state.endMs, state.durationMs, state.mode) {
        if (previewing) {
            skipPending = false
            while (previewing) {
                val position = preview.currentPosition
                if (state.mode == TrimMode.REMOVE_SELECTED && hasAudioAfterEdit) {
                    if (position in state.startMs until state.endMs) {
                        // Una búsqueda manual puede escuchar el intervalo; la reproducción normal lo omite.
                        if (!currentPlayFromSelectedSegment && !skipPending) {
                            preview.seekTo(state.endMs)
                            skipPending = true
                        } else {
                            playheadMs = position
                        }
                    } else {
                        skipPending = false
                        playheadMs = position
                        playFromSelectedSegment = false
                    }
                } else if (state.mode == TrimMode.REMOVE_SELECTED) {
                    playheadMs = position
                } else if (position >= state.endMs) {
                    preview.seekTo(state.startMs)
                    playheadMs = state.startMs
                } else {
                    playheadMs = preview.currentPosition.coerceAtLeast(state.startMs)
                }
                delay(50)
            }
        }
    }
    BackHandler(enabled = !state.saving, onBack = { onClose(state.updatedTrack) })

    Surface(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onClose(state.updatedTrack) }, enabled = !state.saving) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
                Text("Recortar contenido", style = MaterialTheme.typography.titleLarge)
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.mode == TrimMode.KEEP_SELECTED,
                    onClick = {
                        preview.pause()
                        previewing = false
                        playFromSelectedSegment = false
                        vm.setMode(TrimMode.KEEP_SELECTED)
                    },
                    label = { Text("Recortar límites") },
                    enabled = !state.loading && !state.saving,
                )
                FilterChip(
                    selected = state.mode == TrimMode.REMOVE_SELECTED,
                    onClick = {
                        preview.pause()
                        previewing = false
                        playFromSelectedSegment = false
                        vm.setMode(TrimMode.REMOVE_SELECTED)
                    },
                    label = { Text("Quitar tramo") },
                    enabled = !state.loading && !state.saving,
                )
            }

            Spacer(Modifier.height(12.dp))
            TrackArt(track.uri, Modifier.size(220.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                track.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))

            when {
                state.loading -> CircularProgressIndicator()
                state.durationMs > 0 -> {
                    SkinnedRangeSeekBar(
                        startMs = state.startMs,
                        endMs = state.endMs,
                        durationMs = state.durationMs,
                        skin = skin,
                        playheadMs = playheadMs,
                        onRangeChange = { start, end ->
                            playFromSelectedSegment = false
                            skipPending = false
                            vm.setStart(start)
                            vm.setEnd(end)
                        },
                        onPlayheadChange = { position ->
                            // Pausar evita que el avance del reproductor sobrescriba la posición buscada.
                            preview.pause()
                            previewing = false
                            playheadMs = position
                            skipPending = false
                            playFromSelectedSegment =
                                state.mode == TrimMode.REMOVE_SELECTED &&
                                    position in state.startMs until state.endMs
                            preview.seekTo(position)
                        },
                        onPlayheadDragStart = {
                            preview.pause()
                            previewing = false
                        },
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${if (state.mode == TrimMode.REMOVE_SELECTED) "Quitar desde" else "Inicio"}: ${formatTime(state.startMs)}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            "${if (state.mode == TrimMode.REMOVE_SELECTED) "hasta" else "Fin"}: ${formatTime(state.endMs)}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Text(
                        "Posición: ${formatTime(playheadMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TimeControl(
                            label = if (state.mode == TrimMode.REMOVE_SELECTED) "Desde" else "Inicio",
                            time = state.startMs,
                            onStep = {
                                playFromSelectedSegment = false
                                skipPending = false
                                vm.setStart(state.startMs + it)
                            },
                        )
                        FilledIconButton(
                            onClick = {
                                if (previewing) {
                                    playheadMs = preview.currentPosition
                                    preview.pause()
                                    previewing = false
                                } else {
                                    val startPosition = when {
                                        state.mode == TrimMode.KEEP_SELECTED &&
                                            playheadMs >= state.endMs -> state.startMs
                                        else -> playheadMs.coerceIn(
                                            if (state.mode == TrimMode.KEEP_SELECTED) state.startMs else 0L,
                                            state.durationMs,
                                        )
                                    }
                                    preview.seekTo(startPosition)
                                    playheadMs = startPosition
                                    playFromSelectedSegment =
                                        state.mode == TrimMode.REMOVE_SELECTED &&
                                            startPosition in state.startMs until state.endMs
                                    preview.play()
                                    previewing = true
                                }
                            },
                            enabled = !state.saving,
                        ) {
                            Icon(
                                if (previewing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (previewing) "Pausar previsualización" else "Escuchar resultado",
                            )
                        }
                        TimeControl(
                            label = if (state.mode == TrimMode.REMOVE_SELECTED) "Hasta" else "Fin",
                            time = state.endMs,
                            onStep = {
                                playFromSelectedSegment = false
                                skipPending = false
                                vm.setEnd(state.endMs + it)
                            },
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        if (state.mode == TrimMode.REMOVE_SELECTED) {
                            "Se quitará el tramo seleccionado y se unirán las partes anterior y posterior. El original se sustituirá por un M4A/AAC; la conversión puede reducir ligeramente la calidad."
                        } else {
                            "El tramo seleccionado se convertirá a M4A/AAC (192 kb/s) y sustituirá el original en la misma ubicación. Cambiará la extensión a .m4a."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            preview.pause()
                            previewing = false
                            confirmReplace = true
                        },
                        enabled = !state.saving && !state.saved && hasAudioAfterEdit,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("  Guardando recorte…")
                        } else {
                            Text(if (state.mode == TrimMode.REMOVE_SELECTED) "Quitar tramo" else "Guardar recorte")
                        }
                    }
                }
                else -> Unit
            }

            state.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("¿Sustituir el archivo original?") },
            text = {
                Text(
                    if (state.mode == TrimMode.REMOVE_SELECTED) {
                        "Se eliminará el tramo de ${formatTime(state.startMs)} a ${formatTime(state.endMs)} y se unirán las partes restantes. El resultado se guardará como M4A/AAC y sustituirá el original; la conversión puede reducir ligeramente la calidad. No se podrá deshacer."
                    } else {
                        "Se convertirá el tramo seleccionado a M4A/AAC (192 kb/s) y sustituirá el original. La conversión puede reducir ligeramente la calidad. El archivo cambiará a .m4a y no se podrá deshacer."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReplace = false
                        vm.save(track)
                    },
                ) { Text("Sustituir") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReplace = false }) { Text("Cancelar") }
            },
        )
    }

    if (state.saved) {
        AlertDialog(
            onDismissRequest = { onClose(state.updatedTrack) },
            title = { Text(if (state.mode == TrimMode.REMOVE_SELECTED) "Tramo eliminado" else "Archivo recortado") },
            text = {
                Text(
                    if (state.mode == TrimMode.REMOVE_SELECTED) {
                        "El tramo se ha quitado y las partes restantes se han unido en el archivo original."
                    } else {
                        "El audio se ha convertido a M4A y actualizado en su ubicación."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { onClose(state.updatedTrack) }) { Text("Aceptar") }
            },
        )
    }
}

@Composable
private fun TimeControl(label: String, time: Long, onStep: (Long) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(formatTime(time), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { onStep(-1_000) }) { Text("−1 s") }
            OutlinedButton(onClick = { onStep(1_000) }) { Text("+1 s") }
        }
    }
}

private fun formatTime(ms: Long): String {
    val seconds = (ms / 1_000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
