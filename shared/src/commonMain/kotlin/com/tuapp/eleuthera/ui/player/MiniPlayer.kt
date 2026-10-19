package com.tuapp.eleuthera.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tuapp.eleuthera.playback.PlayerState
import com.tuapp.eleuthera.playback.RepeatMode
import com.tuapp.eleuthera.ui.platform.decodeImage

/**
 * Barra compacta sobre la navegación. Solo aparece cuando hay algo cargado.
 * Con [fullControls] (escritorio, donde sobra ancho) lleva también aleatorio, anterior y
 * repetir; en el móvil solo reproducir y siguiente. [trailing] añade controles al final
 * (en escritorio, el volumen).
 */
@Composable
fun MiniPlayer(
    vm: PlayerUi,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    fullControls: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val state by vm.state.collectAsState()
    if (!state.hasMedia) return
    val position by vm.positionMs.collectAsState()
    val skin by vm.progressSkin.collectAsState()
    val fraction = if (state.durationMs > 0) position.toFloat() / state.durationMs else 0f

    Surface(modifier.fillMaxWidth().clickable(onClick = onExpand), tonalElevation = 6.dp) {
        Column {
            val current = skin
            if (current != null) {
                SkinnedTrack(fraction, current)
            } else {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            }
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(state, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        state.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val active = MaterialTheme.colorScheme.primary
                val idle = MaterialTheme.colorScheme.onSurfaceVariant
                if (fullControls) {
                    IconButton(onClick = vm::toggleShuffle) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = if (state.shuffle) "Aleatorio: activado" else "Aleatorio: desactivado",
                            tint = if (state.shuffle) active else idle,
                        )
                    }
                    IconButton(onClick = vm::previous) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Anterior")
                    }
                }
                IconButton(onClick = vm::toggle) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                    )
                }
                IconButton(onClick = vm::next) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Siguiente")
                }
                if (fullControls) {
                    IconButton(onClick = vm::cycleRepeat) {
                        Icon(
                            if (state.repeat == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = when (state.repeat) {
                                RepeatMode.OFF -> "Repetir: desactivado"
                                RepeatMode.ALL -> "Repetir: playlist"
                                RepeatMode.ONE -> "Repetir: canción"
                            },
                            tint = if (state.repeat == RepeatMode.OFF) idle else active,
                        )
                    }
                }
                trailing()
            }
        }
    }
}

/** Carátula de la canción actual, o un icono si el archivo no la trae. */
@Composable
fun Artwork(state: PlayerState, modifier: Modifier = Modifier) {
    // Se recuerda por canción para no recargar la imagen en cada evento del reproductor.
    val art = remember(state.mediaId, state.artwork != null) { state.artwork?.let(::decodeImage) }
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}