package com.tuapp.eleuthera.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.eleuthera.data.playlist.PlaylistRepository
import com.tuapp.eleuthera.data.settings.LibrarySettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Playlists en Android. La lógica de las pantallas está en [PlaylistsController] (común);
 * aquí queda lo propio del móvil: el botón flotante de playlists rápidas.
 */
@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val repo: PlaylistRepository,
    private val librarySettings: LibrarySettings,
) : ViewModel() {

    val controller = PlaylistsController(viewModelScope, repo)

    /** Si el usuario activó el botón flotante de playlists rápidas (Ajustes > Biblioteca). */
    val quickAccess: StateFlow<Boolean> = librarySettings.quickPlaylists
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Posición guardada del botón flotante (fracciones 0..1), o null si nunca se movió. */
    val quickButtonPosition: StateFlow<Pair<Float, Float>?> = librarySettings.quickButtonPosition
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun saveQuickButtonPosition(x: Float, y: Float) {
        viewModelScope.launch { librarySettings.setQuickButtonPosition(x, y) }
    }

    init {
        viewModelScope.launch { runCatching { repo.importDownloadedFoldersOnce() } }
    }
}