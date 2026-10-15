package com.tuapp.eleuthera

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.ui.library.LibraryScreen
import com.tuapp.eleuthera.ui.library.TrackActions
import com.tuapp.eleuthera.ui.player.MiniPlayer
import com.tuapp.eleuthera.ui.player.NowPlayingScreen
import com.tuapp.eleuthera.ui.player.PlayerViewModel
import com.tuapp.eleuthera.ui.playlists.AddToPlaylistDialog
import com.tuapp.eleuthera.ui.playlists.PlaylistsScreen
import com.tuapp.eleuthera.ui.playlists.PlaylistsViewModel
import com.tuapp.eleuthera.ui.search.SearchScreen
import com.tuapp.eleuthera.ui.settings.SettingsScreen
import com.tuapp.eleuthera.ui.settings.ThemeViewModel
import com.tuapp.eleuthera.ui.theme.AppTheme
import com.tuapp.eleuthera.ui.update.UpdatePrompt
import dagger.hilt.android.AndroidEntryPoint

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
    LaunchedEffect(openPlayerRequest) {
        if (openPlayerRequest) {
            showPlayer = true
            onOpenPlayerRequestHandled()
        }
    }
    // Canción para la que se está eligiendo playlist (null = diálogo cerrado).
    var trackForPlaylist by remember { mutableStateOf<Track?>(null) }

    val trackActions = remember(playerVm) {
        TrackActions(
            onPlay = playerVm::play,
            onShuffle = playerVm::playShuffled,
            onPlayNext = playerVm::playNext,
            onAddToQueue = playerVm::addToQueue,
            onAddToPlaylist = { trackForPlaylist = it },
        )
    }

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
            val modifier = Modifier.fillMaxSize().padding(padding)
            when (section) {
                Section.SEARCH -> SearchScreen(modifier)
                Section.LIBRARY -> LibraryScreen(modifier, trackActions)
                Section.PLAYLISTS -> PlaylistsScreen(modifier, trackActions, playlistsVm)
                Section.SETTINGS -> SettingsScreen(modifier, themeVm)
            }
        }

        // Aviso de versión nueva publicada en GitHub.
        UpdatePrompt()

        trackForPlaylist?.let { track ->
            AddToPlaylistDialog(track, playlistsVm, onDismiss = { trackForPlaylist = null })
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
            )
        }
    }
}