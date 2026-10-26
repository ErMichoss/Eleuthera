package com.tuapp.eleuthera.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tuapp.eleuthera.data.settings.LoadedSkin
import com.tuapp.eleuthera.playback.PlayerState
import com.tuapp.eleuthera.playback.QueueItem
import com.tuapp.eleuthera.playback.RepeatMode
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Pantalla completa del reproductor, con la cola integrada. [extraControls] va debajo de
 * los botones (en escritorio, el volumen).
 */
@Composable
fun NowPlayingScreen(
    vm: PlayerUi,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    extraControls: @Composable () -> Unit = {},
    extraContent: @Composable (Long) -> Unit = {},
) {
    val state by vm.state.collectAsState()
    val position by vm.positionMs.collectAsState()
    var showQueue by rememberSaveable { mutableStateOf(false) }

    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Cerrar")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showQueue = !showQueue }) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Cola",
                        tint = if (showQueue) MaterialTheme.colorScheme.primary
                        else LocalContentColor.current,
                    )
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (showQueue) {
                    QueueList(state, vm)
                } else {
                    // El cuadrado más grande que quepa: en móvil manda el ancho; en escritorio, el alto.
                    Artwork(
                        state,
                        Modifier.aspectRatio(1f).clip(RoundedCornerShape(16.dp)),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                state.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                state.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            val skin by vm.progressSkin.collectAsState()
            SeekBar(position, state.durationMs, skin, state.isPlaying, vm::seekTo)
            extraContent(position)
            Controls(state, vm)
            extraControls()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun LyricsContent(
    text: String?,
    loading: Boolean,
    error: String?,
    positionMs: Long,
    subtitleMessage: String? = null,
) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    val message = when {
        loading -> "Buscando letras…"
        error != null -> "No se pudieron leer las letras: $error"
        text.isNullOrBlank() -> "No se encontraron letras incrustadas ni en LRCLIB para esta canción."
        else -> null
    }
    val lines = remember(text) { text?.let(::parseLyricsLines).orEmpty() }
    val activeIndex = lines.indexOfLast { it.startMs != null && it.startMs <= positionMs }
    val visibleLines = if (expanded || lines.size <= LYRICS_PREVIEW_LINES) {
        lines.indices.toList()
    } else {
        val first = (activeIndex - 1).coerceAtLeast(0)
            .coerceAtMost(lines.size - LYRICS_PREVIEW_LINES)
        (first until first + LYRICS_PREVIEW_LINES).toList()
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .heightIn(min = 88.dp, max = 240.dp)
            .clickable(enabled = !text.isNullOrBlank()) { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(
            Modifier.padding(16.dp).then(
                if (expanded) Modifier.verticalScroll(rememberScrollState()) else Modifier,
            ),
        ) {
            Text(
                if (expanded) "Letra completa" else "Letra · toca para ampliar",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            if (message != null) {
                Text(message, style = MaterialTheme.typography.bodyLarge)
            } else {
                visibleLines.forEach { index ->
                    Text(
                        text = lines[index].text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (index == activeIndex && lines[index].startMs != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        fontWeight = if (index == activeIndex && lines[index].startMs != null) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        },
                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            subtitleMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private data class LyricsLine(val startMs: Long?, val text: String)

private fun parseLyricsLines(text: String): List<LyricsLine> {
    val lrcLines = text.lineSequence().flatMap { line ->
        val timestamps = LRC_TIMESTAMP.findAll(line).toList()
        val lyric = LRC_TIMESTAMP.replace(line, "").trim()
        if (lyric.isBlank() || LRC_METADATA.matches(lyric)) {
            emptySequence()
        } else {
            timestamps.asSequence().mapNotNull { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                val seconds = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0
                LyricsLine((minutes * 60 + seconds) * 1_000 + fraction, lyric)
            }
        }
    }.toList()
    if (lrcLines.isNotEmpty()) {
        return lrcLines.sortedBy { it.startMs }
            .groupBy { it.startMs }
            .map { (startMs, sameTimeLines) ->
                LyricsLine(startMs, sameTimeLines.map { it.text }.distinct().joinToString("\n"))
            }
    }

    val srtLines = text.split(Regex("""\r?\n\s*\r?\n""")).flatMap { block ->
        val blockLines = block.lines()
        val timestampIndex = blockLines.indexOfFirst { SRT_TIMESTAMP.matches(it.trim()) }
        if (timestampIndex == -1) emptyList() else {
            val match = SRT_TIMESTAMP.matchEntire(blockLines[timestampIndex].trim())!!
            val startMs = parseSrtTimestamp(match.groupValues[1])
            blockLines.drop(timestampIndex + 1)
                .filter(String::isNotBlank)
                .map { LyricsLine(startMs, it.trim()) }
        }
    }
    if (srtLines.isNotEmpty()) return srtLines

    return text.lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .map { LyricsLine(null, it) }
        .toList()
}

private fun parseSrtTimestamp(value: String): Long? {
    val match = SRT_TIME.matchEntire(value) ?: return null
    val hours = match.groupValues[1].toLongOrNull() ?: return null
    val minutes = match.groupValues[2].toLongOrNull() ?: return null
    val seconds = match.groupValues[3].toLongOrNull() ?: return null
    val millis = match.groupValues[4].toLongOrNull() ?: return null
    return ((hours * 60 + minutes) * 60 + seconds) * 1_000 + millis
}

private const val LYRICS_PREVIEW_LINES = 4
private val LRC_TIMESTAMP = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
private val LRC_METADATA = Regex("""\[(?:ar|al|ti|by|re|ve|length|offset|kana|la):.*]""", RegexOption.IGNORE_CASE)
private val SRT_TIMESTAMP = Regex("""(\d{2}:\d{2}:\d{2}[,.]\d{3})\s+-->\s+(\d{2}:\d{2}:\d{2}[,.]\d{3})""")
private val SRT_TIME = Regex("""(\d{2}):(\d{2}):(\d{2})[,.](\d{3})""")

@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    skin: LoadedSkin?,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
) {
    // Mientras se arrastra se muestra el valor del dedo, no el del reproductor.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(1).toFloat()
    val shown = dragging ?: positionMs.toFloat().coerceIn(0f, duration)

    val finish = {
        dragging?.let { onSeek(it.toLong()) }
        dragging = null
    }
    if (skin != null) {
        SkinnedSeekBar(
            fraction = shown / duration,
            skin = skin,
            isPlaying = isPlaying,
            onScrub = { dragging = it * duration },
            onScrubEnd = finish,
            enabled = durationMs > 0,
        )
    } else {
        Slider(
            value = shown,
            onValueChange = { dragging = it },
            onValueChangeFinished = finish,
            valueRange = 0f..duration,
            enabled = durationMs > 0,
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatTime(shown.toLong()), style = MaterialTheme.typography.labelSmall)
        Text(formatTime(durationMs), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Controls(state: PlayerState, vm: PlayerUi) {
    val active = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = vm::toggleShuffle) {
            Icon(
                Icons.Default.Shuffle,
                contentDescription = "Aleatorio",
                tint = if (state.shuffle) active else idle,
            )
        }
        IconButton(onClick = vm::previous, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Default.SkipPrevious, "Anterior", Modifier.size(36.dp))
        }
        FilledIconButton(onClick = vm::toggle, modifier = Modifier.size(72.dp)) {
            Icon(
                if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                modifier = Modifier.size(40.dp),
            )
        }
        IconButton(onClick = vm::next, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Default.SkipNext, "Siguiente", Modifier.size(36.dp))
        }
        IconButton(onClick = vm::cycleRepeat) {
            Icon(
                if (state.repeat == RepeatMode.ONE) Icons.Default.RepeatOne
                else Icons.Default.Repeat,
                contentDescription = when (state.repeat) {
                    RepeatMode.OFF -> "Repetir: desactivado"
                    RepeatMode.ALL -> "Repetir: playlist"
                    RepeatMode.ONE -> "Repetir: canción"
                },
                tint = if (state.repeat == RepeatMode.OFF) idle else active,
            )
        }
    }
}

@Composable
private fun QueueList(state: PlayerState, vm: PlayerUi) {
    // Copia local: se reordena al instante mientras se arrastra y se confirma al soltar.
    val upcoming = state.userQueue + state.upNext
    var local by remember { mutableStateOf(upcoming) }
    LaunchedEffect(upcoming) { local = upcoming }
    var draggedKey by remember { mutableStateOf<Any?>(null) }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val f = local.indexOfFirst { it.key == from.key }
        val t = local.indexOfFirst { it.key == to.key }
        // Solo se reordena dentro de la misma sección: tu cola o la de la playlist.
        if (f < 0 || t < 0 || local[f].userQueued != local[t].userQueued) return@rememberReorderableLazyListState
        draggedKey = from.key
        local = local.toMutableList().apply { add(t, removeAt(f)) }
    }

    fun commitDrag() {
        val key = draggedKey ?: return
        draggedKey = null
        val from = upcoming.firstOrNull { it.key == key }?.index ?: return
        val to = state.currentIndex + 1 + local.indexOfFirst { it.key == key }
        vm.move(from, to)
    }

    val userItems = local.filter { it.userQueued }
    val defaultItems = local.filterNot { it.userQueued }

    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        item(key = "now") {
            SectionHeader("Sonando ahora")
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                headlineContent = {
                    Text(
                        state.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                supportingContent = { Text(state.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }

        if (userItems.isNotEmpty()) {
            item(key = "header_user") { SectionHeader("Siguiente en tu cola") }
            items(userItems, key = { it.key }) { item ->
                QueueRow(item, reorderState, vm, onDragStopped = ::commitDrag)
            }
        }

        item(key = "header_default") {
            SectionHeader(
                when {
                    state.shuffle -> "A continuación · aleatorio"
                    else -> "A continuación"
                }
            )
        }
        if (defaultItems.isEmpty()) {
            item(key = "empty") {
                Text(
                    if (state.repeat == RepeatMode.ALL) "Al terminar, la playlist vuelve a empezar."
                    else "No hay más canciones en la cola.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        items(defaultItems, key = { it.key }) { item ->
            QueueRow(item, reorderState, vm, onDragStopped = ::commitDrag)
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun LazyItemScope.QueueRow(
    item: QueueItem,
    reorderState: ReorderableLazyListState,
    vm: PlayerUi,
    onDragStopped: () -> Unit,
) {
    ReorderableItem(reorderState, key = item.key) { dragging ->
        ListItem(
            modifier = Modifier.clickable { vm.jumpTo(item.index) },
            colors = ListItemDefaults.colors(
                containerColor = if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh
                else Color.Transparent,
            ),
            headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(item.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.remove(item.index) }) {
                        Icon(Icons.Default.Close, contentDescription = "Quitar de la cola")
                    }
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = "Arrastrar para reordenar",
                        modifier = Modifier
                            .draggableHandle(onDragStopped = { onDragStopped() })
                            .padding(12.dp),
                    )
                }
            },
        )
    }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}