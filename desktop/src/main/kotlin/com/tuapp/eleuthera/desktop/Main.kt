package com.tuapp.eleuthera.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tuapp.eleuthera.data.library.Covers
import com.tuapp.eleuthera.domain.model.ThemeConfig
import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.platform.DesktopPaths
import com.tuapp.eleuthera.playback.PlayerState
import com.tuapp.eleuthera.playback.RepeatMode
import com.tuapp.eleuthera.ui.theme.AppTheme
import java.text.Normalizer
import javax.swing.UIManager
import kotlinx.coroutines.launch

/**
 * Punto de entrada de la app de escritorio. De momento: la biblioteca (fase 4) y el
 * reproductor con su cola (fase 3). Las pantallas de Android llegan en la fase 5.
 */
fun main() {
    // Diálogos de Swing (elegir carpeta) con el aspecto del sistema, no el de Java.
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    // yt-dlp, ffmpeg y Deno: se descargan solos si faltan.
    AppGraph.prepareTools()

    application {
        Window(
            onCloseRequest = {
                AppGraph.player.release()
                exitApplication()
            },
            title = "Eleuthera",
            state = rememberWindowState(width = 1200.dp, height = 760.dp),
        ) {
            val theme by AppGraph.themeRepository.config.collectAsState(ThemeConfig())
            AppTheme(theme) { MainScreen() }
        }
    }
}

private enum class Tab(val label: String) { LIBRARY("Biblioteca"), DOWNLOADS("Descargar") }

@Composable
private fun MainScreen() {
    val player = AppGraph.player
    val state by player.state.collectAsState()
    val error by player.error.collectAsState()
    val library by AppGraph.library.tracks.collectAsState()
    val pendingTags by AppGraph.library.pendingTags.collectAsState()
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(Tab.LIBRARY) }

    // Biblioteca ordenada (artista, álbum, título) con el texto en el que se busca ya
    // preparado: así filtrar al escribir es instantáneo aunque haya miles de canciones.
    val indexed = remember(library) {
        library.orEmpty()
            .sortedWith(
                compareBy<Track>({ it.artist.lowercase() }, { it.album.lowercase() }, { it.title.lowercase() })
            )
            .map { it to searchable("${it.title} ${it.artist} ${it.album} ${it.folder}") }
    }
    // Cada palabra escrita tiene que aparecer en algún sitio; sin importar tildes ni mayúsculas.
    val tracks = remember(indexed, query) {
        val words = searchable(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) indexed.map { it.first }
        else indexed.filter { (_, text) -> words.all { it in text } }.map { it.first }
    }
    val current = remember(library, state.mediaId) {
        library?.firstOrNull { it.id.toString() == state.mediaId }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Eleuthera", style = MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tab.entries.forEach { t ->
                        FilterChip(
                            selected = tab == t,
                            onClick = { tab = t },
                            label = { Text(t.label) },
                            leadingIcon = {
                                Icon(
                                    if (t == Tab.LIBRARY) Icons.Default.LibraryMusic else Icons.Default.Download,
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                }
                if (tab == Tab.LIBRARY) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text("Buscar canción, artista o álbum") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (query.isNotEmpty()) {
                            { IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Borrar") } }
                        } else null,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        when {
                            library == null -> "Leyendo carpetas…"
                            pendingTags > 0 -> "${library!!.size} canciones · leyendo etiquetas ($pendingTags)"
                            else -> "${library!!.size} canciones"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
            ToolsBanner()
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            HorizontalDivider()

            if (tab == Tab.DOWNLOADS) {
                DownloadsScreen(Modifier.weight(1f).fillMaxWidth())
            } else Row(Modifier.weight(1f).fillMaxWidth()) {
                FoldersPanel(Modifier.width(240.dp).fillMaxHeight())
                VerticalDivider()
                TrackList(
                    tracks = tracks,
                    playingId = state.mediaId,
                    emptyText = when {
                        library == null -> "Leyendo carpetas…"
                        query.isNotBlank() -> "Nada coincide con «${query.trim()}»."
                        else -> "No hay canciones. Añade una carpeta con música a la izquierda."
                    },
                    query = query,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                QueuePanel(state, Modifier.width(300.dp).fillMaxHeight())
            }

            HorizontalDivider()
            Controls(state, current)
        }
    }
}

/** Carpetas que se leen: la de descargas fija y las que añada el usuario. */
@Composable
private fun FoldersPanel(modifier: Modifier) {
    val settings = AppGraph.librarySettings
    val extra by settings.extraFolders.collectAsState(emptyList())
    val scope = rememberCoroutineScope()

    Column(modifier.padding(12.dp)) {
        Text("Carpetas", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        FolderRow(DesktopPaths.musicDir.path, "Descargas", onRemove = null)
        extra.forEach { dir ->
            FolderRow(dir.path, dir.name.ifEmpty { dir.path }) { scope.launch { settings.remove(dir) } }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = {
            FolderPicker.choose()?.let { dir -> scope.launch { settings.add(dir) } }
        }) {
            Icon(Icons.Default.CreateNewFolder, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Añadir carpeta")
        }
        TextButton(onClick = AppGraph.library::refresh) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Actualizar")
        }
    }
}

@Composable
private fun FolderRow(path: String, label: String, onRemove: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                path, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(Icons.Default.Close, contentDescription = "Quitar carpeta") }
        } else {
            Spacer(Modifier.height(48.dp))
        }
    }
}

/** Canciones: clic para reproducir desde ahí; botón para añadir a la cola. */
@Composable
private fun TrackList(
    tracks: List<Track>,
    playingId: String?,
    emptyText: String,
    query: String,
    modifier: Modifier,
) {
    val player = AppGraph.player
    val listState = rememberLazyListState()
    // Al cambiar la búsqueda se vuelve arriba, para ver los primeros resultados.
    LaunchedEffect(query) { listState.scrollToItem(0) }
    if (tracks.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(modifier, state = listState) {
        itemsIndexed(tracks, key = { _, t -> t.id }) { i, track ->
            val playing = playingId == track.id.toString()
            Row(
                Modifier.fillMaxWidth()
                    .background(if (playing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                    .clickable { player.play(tracks, i) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(track, Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (playing) FontWeight.Bold else null,
                    )
                    Text(
                        "${track.artist} · ${track.album}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (track.durationMs > 0) {
                    Text(time(track.durationMs), style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { player.addToQueue(track) }) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Añadir a la cola")
                }
            }
        }
    }
}

/** Lo que va a sonar después. Clic para saltar a esa canción. */
@Composable
private fun QueuePanel(state: PlayerState, modifier: Modifier) {
    val player = AppGraph.player
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp)) {
        Text("A continuación", style = MaterialTheme.typography.titleMedium)
        LazyColumn {
            val queue = state.userQueue + state.upNext
            itemsIndexed(queue.take(100), key = { _, q -> q.key }) { _, q ->
                Text(
                    (if (q.userQueued) "＋ " else "") + q.title,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable { player.jumpTo(q.index) }.padding(vertical = 4.dp),
                )
            }
        }
    }
}

/** Carátula de la canción, o un icono de nota si no tiene. Se carga en segundo plano. */
@Composable
private fun Cover(track: Track?, modifier: Modifier) {
    val image by produceState<ImageBitmap?>(null, track?.id) {
        value = track?.let { Covers.load(it) }
    }
    Box(
        modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val img = image
        if (img != null) {
            Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Controls(state: PlayerState, current: Track?) {
    val player = AppGraph.player
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1)
    val position = dragging ?: (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(current, Modifier.size(56.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(state.title.ifEmpty { "Nada sonando" }, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(state.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            val on = MaterialTheme.colorScheme.primary
            val off = LocalContentColor.current
            IconButton(onClick = player::toggleShuffle) {
                Icon(Icons.Default.Shuffle, "Aleatorio", tint = if (state.shuffle) on else off)
            }
            IconButton(onClick = player::previous) { Icon(Icons.Default.SkipPrevious, "Anterior") }
            IconButton(onClick = player::toggle) {
                Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Reproducir o pausar")
            }
            IconButton(onClick = player::next) { Icon(Icons.Default.SkipNext, "Siguiente") }
            IconButton(onClick = player::cycleRepeat) {
                Icon(
                    if (state.repeat == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    "Repetir",
                    tint = if (state.repeat == RepeatMode.OFF) off else on,
                )
            }
            Spacer(Modifier.width(16.dp))
            VolumeControl()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(time(state.positionMs), style = MaterialTheme.typography.labelSmall)
            Slider(
                value = position,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { player.seekTo((it * state.durationMs).toLong()) }
                    dragging = null
                },
                enabled = state.hasMedia && state.durationMs > 0,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(time(state.durationMs), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Minúsculas y sin tildes: "Canción" y "cancion" se encuentran igual. */
private fun searchable(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")

private val DIACRITICS = Regex("\\p{Mn}+")

private fun time(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

/**
 * Barra de volumen. El icono silencia o recupera el volumen anterior; el valor se guarda al
 * soltar la barra, para no escribir en disco con cada movimiento.
 */
@Composable
private fun VolumeControl() {
    val player = AppGraph.player
    val volume by player.volume.collectAsState()
    val scope = rememberCoroutineScope()
    var beforeMute by remember { mutableStateOf(1f) }

    fun save() = scope.launch { AppGraph.playerSettings.setVolume(player.volume.value) }

    IconButton(onClick = {
        if (volume > 0f) { beforeMute = volume; player.setVolume(0f) }
        else player.setVolume(beforeMute.takeIf { it > 0f } ?: 1f)
        save()
    }) {
        Icon(
            when {
                volume == 0f -> Icons.AutoMirrored.Filled.VolumeOff
                volume < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
                else -> Icons.AutoMirrored.Filled.VolumeUp
            },
            contentDescription = "Silenciar",
        )
    }
    Slider(
        value = volume,
        onValueChange = player::setVolume,
        onValueChangeFinished = { save() },
        modifier = Modifier.width(140.dp),
    )
}