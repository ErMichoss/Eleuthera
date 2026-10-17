package com.tuapp.eleuthera.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tuapp.eleuthera.domain.model.ThemeConfig
import com.tuapp.eleuthera.ui.common.LocalNowPlaying
import com.tuapp.eleuthera.ui.library.LibraryContent
import com.tuapp.eleuthera.ui.library.TrackActions
import com.tuapp.eleuthera.ui.platform.LocalArtLoader
import com.tuapp.eleuthera.ui.platform.LocalToast
import com.tuapp.eleuthera.ui.player.MiniPlayer
import com.tuapp.eleuthera.ui.player.NowPlayingScreen
import com.tuapp.eleuthera.ui.playlists.AddToPlaylistDialog
import com.tuapp.eleuthera.ui.playlists.PlaylistTarget
import com.tuapp.eleuthera.ui.playlists.PlaylistsContent
import com.tuapp.eleuthera.platform.FileUri
import com.tuapp.eleuthera.ui.theme.AppTheme
import javax.swing.UIManager
import kotlinx.coroutines.launch

/** Punto de entrada de la app de escritorio. */
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
            state = rememberWindowState(width = 1200.dp, height = 800.dp),
        ) {
            val theme by AppGraph.themeRepository.config.collectAsState(ThemeConfig())
            AppTheme(theme) { App() }
        }
    }
}

/** Secciones de la barra lateral. Buscar y Ajustes llegan en la fase 5c. */
private enum class Section(val label: String, val icon: ImageVector) {
    LIBRARY("Biblioteca", Icons.Default.LibraryMusic),
    PLAYLISTS("Playlists", Icons.AutoMirrored.Filled.PlaylistPlay),
    DOWNLOADS("Descargar", Icons.Default.Download),
    FOLDERS("Carpetas", Icons.Default.Folder),
}

/**
 * Raíz de la app, como AppRoot en Android: barra lateral en vez de inferior, el mini
 * reproductor abajo y el reproductor completo por encima de todo.
 */
@Composable
private fun App() {
    val player = AppGraph.player
    val playerUi = AppGraph.playerUi
    val nowPlaying by playerUi.nowPlaying.collectAsState()
    val scope = rememberCoroutineScope()
    var section by remember { mutableStateOf(Section.LIBRARY) }
    var showPlayer by remember { mutableStateOf(false) }
    // Canción o lista para la que se está eligiendo playlist (null = diálogo cerrado).
    var playlistTarget by remember { mutableStateOf<PlaylistTarget?>(null) }

    // Avisos de las pantallas comunes (lo que en Android es un Toast).
    val snackbar = remember { SnackbarHostState() }
    val toast: (String) -> Unit = remember {
        { msg: String ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(msg)
            }
            Unit
        }
    }
    val playerError by player.error.collectAsState()
    LaunchedEffect(playerError) { playerError?.let(toast) }

    val actions = remember {
        TrackActions(
            onPlay = { tracks, index -> player.play(tracks, index) },
            onShuffle = player::playShuffled,
            onPlayNext = { player.playNext(it) },
            onAddToQueue = { player.addToQueue(it) },
            onAddToPlaylist = { playlistTarget = PlaylistTarget(it) },
            onPlayNextAll = { player.playNext(it) },
            onAddAllToQueue = { player.addToQueue(it) },
            onAddAllToPlaylist = { label, tracks -> playlistTarget = PlaylistTarget(label, tracks) },
        )
    }

    CompositionLocalProvider(
        LocalNowPlaying provides nowPlaying,
        LocalToast provides toast,
        LocalArtLoader provides desktopArtLoader,
    ) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
                Row(Modifier.fillMaxSize().padding(padding)) {
                    NavigationRail {
                        Section.entries.forEach { item ->
                            NavigationRailItem(
                                selected = item == section,
                                onClick = { section = item },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        ToolsBanner()
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when (section) {
                                Section.LIBRARY -> LibraryContent(
                                    controller = AppGraph.libraryModel.controller,
                                    actions = actions,
                                    onConfirmDelete = { track ->
                                        scope.launch { AppGraph.libraryModel.delete(track)?.let(toast) }
                                    },
                                    emptyHint = "Aún no hay música.\n" +
                                            "Descarga algo en «Descargar» o añade una carpeta en «Carpetas».",
                                )
                                Section.PLAYLISTS -> PlaylistsContent(
                                    vm = AppGraph.playlists,
                                    actions = actions,
                                    pickImage = { onPicked ->
                                        FolderPicker.chooseImage()?.let { onPicked(FileUri(it)) }
                                    },
                                )
                                Section.DOWNLOADS -> DownloadsScreen(Modifier.fillMaxSize())
                                Section.FOLDERS -> FoldersScreen(Modifier.fillMaxSize())
                            }
                        }
                        MiniPlayer(playerUi, onExpand = { showPlayer = true }, fullControls = true) {
                            Spacer(Modifier.width(8.dp))
                            VolumeControl()
                        }
                    }
                }
            }

            playlistTarget?.let { target ->
                AddToPlaylistDialog(target, AppGraph.playlists, onDismiss = { playlistTarget = null })
            }

            // Reproductor completo, por encima de todo. Centrado para que en una ventana
            // ancha no se estire.
            AnimatedVisibility(
                visible = showPlayer,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    NowPlayingScreen(
                        vm = playerUi,
                        onClose = { showPlayer = false },
                        modifier = Modifier.widthIn(max = 640.dp),
                        extraControls = {
                            Row(
                                Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) { VolumeControl() }
                        },
                    )
                }
            }
        }
    }
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
        if (volume > 0f) {
            beforeMute = volume
            player.setVolume(0f)
        } else {
            player.setVolume(beforeMute.takeIf { it > 0f } ?: 1f)
        }
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