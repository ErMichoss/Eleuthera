package com.tuapp.eleuthera

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import com.tuapp.eleuthera.ui.library.LibraryScreen
import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.ui.common.LocalNowPlaying
import com.tuapp.eleuthera.ui.common.androidArtLoader
import com.tuapp.eleuthera.ui.platform.LocalArtLoader
import com.tuapp.eleuthera.ui.platform.LocalRemoteImage
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.tuapp.eleuthera.ui.platform.LocalToast
import com.tuapp.eleuthera.ui.library.TrackActions
import com.tuapp.eleuthera.ui.player.TrimEditorScreen
import com.tuapp.eleuthera.ui.player.MiniPlayer
import com.tuapp.eleuthera.ui.player.NowPlayingScreen
import com.tuapp.eleuthera.ui.player.PlayerViewModel
import com.tuapp.eleuthera.ui.player.LyricsCard
import com.tuapp.eleuthera.ui.playlists.AddToPlaylistDialog
import com.tuapp.eleuthera.ui.playlists.PlaylistTarget
import com.tuapp.eleuthera.ui.playlists.PlaylistsScreen
import com.tuapp.eleuthera.ui.playlists.PlaylistsViewModel
import com.tuapp.eleuthera.ui.playlists.QuickPlaylistsOverlay
import com.tuapp.eleuthera.ui.search.SearchScreen
import com.tuapp.eleuthera.ui.settings.SettingsScreen
import com.tuapp.eleuthera.ui.settings.ThemeViewModel
import com.tuapp.eleuthera.ui.theme.AppTheme
import com.tuapp.eleuthera.ui.update.UpdatePrompt
import dagger.hilt.android.AndroidEntryPoint

/** Miniaturas de YouTube en las pantallas comunes (Buscar): las pinta coil. */
private val coilImage: @Composable (String, Modifier) -> Unit = { url, mod ->
    AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = mod)
}

private enum class Section(val label: String, val icon: ImageVector) {
    SEARCH("Buscar", Icons.Default.Search),
    LIBRARY("Biblioteca", Icons.Default.LibraryMusic),
    PLAYLISTS("Playlists", Icons.AutoMirrored.Filled.PlaylistPlay),
    SETTINGS("Ajustes", Icons.Default.Settings),
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var openPlayerRequest by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openPlayerRequest = intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)
        intent.removeExtra(EXTRA_OPEN_PLAYER)
        enableEdgeToEdge()
        setContent {
            // Android 13+: sin este permiso no se ve el progreso de las descargas.
            val askNotifications =
                rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // Tema global: cualquier cambio en Ajustes recompone toda la app al instante.
            val themeVm: ThemeViewModel = hiltViewModel()
            val theme by themeVm.config.collectAsStateWithLifecycle()

            // Iconos de la barra de estado oscuros o claros según el fondo elegido.
            val view = LocalView.current
            val lightBackground = Color(theme.background).luminance() > 0.5f
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = lightBackground
                    isAppearanceLightNavigationBars = lightBackground
                }
            }

            AppTheme(theme) {
                AppRoot(
                    themeVm = themeVm,
                    openPlayerRequest = openPlayerRequest,
                    onOpenPlayerRequestHandled = { openPlayerRequest = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openPlayerRequest = intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)
        intent.removeExtra(EXTRA_OPEN_PLAYER)
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "com.tuapp.eleuthera.EXTRA_OPEN_PLAYER"
    }
}

@Composable
private fun AppRoot(
    playerVm: PlayerViewModel = hiltViewModel(),
    playlistsVm: PlaylistsViewModel = hiltViewModel(),
    themeVm: ThemeViewModel = hiltViewModel(),
    openPlayerRequest: Boolean,
    onOpenPlayerRequestHandled: () -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(Section.SEARCH) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var trackToTrim by remember { mutableStateOf<Track?>(null) }
    var resumeAfterTrim by remember { mutableStateOf(false) }
    // Canción o lista para la que se está eligiendo playlist (null = diálogo cerrado).
    var playlistTarget by remember { mutableStateOf<PlaylistTarget?>(null) }
    LaunchedEffect(openPlayerRequest) {
        if (openPlayerRequest) {
            showPlayer = true
            onOpenPlayerRequestHandled()
        }
    }
    val trackActions = remember(playerVm) {
        TrackActions(
            onPlay = playerVm::play,
            onShuffle = playerVm::playShuffled,
            onPlayNext = playerVm::playNext,
            onAddToQueue = playerVm::addToQueue,
            onAddToPlaylist = { playlistTarget = PlaylistTarget(it) },
            onTrim = { track ->
                resumeAfterTrim = playerVm.state.value.isPlaying
                if (resumeAfterTrim) playerVm.toggle()
                trackToTrim = track
            },
            onPlayNextAll = playerVm::playNextAll,
            onAddAllToQueue = playerVm::addAllToQueue,
            onAddAllToPlaylist = { label, tracks -> playlistTarget = PlaylistTarget(label, tracks) },
        )
    }

    // Botón flotante de playlists rápidas (opcional, Ajustes > Biblioteca).
    val quickAccess by playlistsVm.quickAccess.collectAsStateWithLifecycle()

    // Qué canción suena: lo leen las listas para resaltarla.
    val nowPlaying by playerVm.nowPlaying.collectAsStateWithLifecycle()
    val lyrics by playerVm.lyrics.collectAsStateWithLifecycle()
    val currentMediaUri by playerVm.currentMediaUri.collectAsStateWithLifecycle()

    // Las pantallas comunes (:shared) piden a la plataforma los avisos y las carátulas.
    val context = LocalContext.current
    val toast = remember(context) { { msg: String -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() } }
    val artLoader = remember(context) { androidArtLoader(context.applicationContext) }

    CompositionLocalProvider(
        LocalNowPlaying provides nowPlaying,
        LocalToast provides toast,
        LocalArtLoader provides artLoader,
        LocalRemoteImage provides coilImage,
    ) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = {
                    Column {
                        MiniPlayer(playerVm, onExpand = { showPlayer = true })
                        NavigationBar {
                            Section.entries.forEach { item ->
                                NavigationBarItem(
                                    selected = item == section,
                                    onClick = { section = item },
                                    icon = { Icon(item.icon, contentDescription = null) },
                                    label = { Text(item.label) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    val modifier = Modifier.fillMaxSize()
                    when (section) {
                        Section.SEARCH -> SearchScreen(modifier)
                        Section.LIBRARY -> LibraryScreen(modifier, trackActions)
                        Section.PLAYLISTS -> PlaylistsScreen(modifier, trackActions, playlistsVm)
                        Section.SETTINGS -> SettingsScreen(modifier, themeVm)
                    }

                    // Playlists rápidas: botón arrastrable por encima de la pantalla actual.
                    if (quickAccess && section != Section.SETTINGS) {
                        // Solo se cargan las playlists mientras el botón está visible.
                        val playlists by playlistsVm.controller.playlists.collectAsStateWithLifecycle()
                        val position by playlistsVm.quickButtonPosition.collectAsStateWithLifecycle()
                        QuickPlaylistsOverlay(
                            playlists = playlists,
                            actions = trackActions,
                            loadTracks = playlistsVm.controller::tracksOf,
                            savedPosition = position,
                            onPositionChange = playlistsVm::saveQuickButtonPosition,
                        )
                    }
                }
            }

            // Aviso de versión nueva publicada en GitHub.
            UpdatePrompt()

            playlistTarget?.let { target ->
                AddToPlaylistDialog(target, playlistsVm.controller, onDismiss = { playlistTarget = null })
            }

            trackToTrim?.let { track ->
                TrimEditorScreen(
                    track = track,
                    onClose = { updatedTrack ->
                        if (updatedTrack != null) playerVm.updateTrack(track.id, updatedTrack)
                        trackToTrim = null
                        if (resumeAfterTrim) playerVm.toggle()
                        resumeAfterTrim = false
                    },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }

            // Reproductor a pantalla completa, por encima de todo.
            AnimatedVisibility(
                visible = showPlayer,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                BackHandler { showPlayer = false }
                NowPlayingScreen(
                    vm = playerVm,
                    onClose = { showPlayer = false },
                    modifier = Modifier.safeDrawingPadding(),
                    extraContent = { positionMs ->
                        if (nowPlaying.mediaId != null) {
                            LyricsCard(
                                text = lyrics.text.takeIf { lyrics.mediaUri == currentMediaUri },
                                loading = lyrics.loading || lyrics.mediaUri != currentMediaUri,
                                error = lyrics.error.takeIf { lyrics.mediaUri == currentMediaUri },
                                positionMs = positionMs,
                                subtitleMessage = lyrics.subtitleMessage
                                    .takeIf { lyrics.mediaUri == currentMediaUri },
                            )
                        }
                    },
                )
            }
        }
    }
}