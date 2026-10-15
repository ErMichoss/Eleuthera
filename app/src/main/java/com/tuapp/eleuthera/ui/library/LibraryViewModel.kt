package com.tuapp.eleuthera.ui.library

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.eleuthera.data.library.MediaLibrary
import com.tuapp.eleuthera.domain.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryTab(val label: String) {
    SONGS("Canciones"), ARTISTS("Artistas"), ALBUMS("Álbumes"), FOLDERS("Carpetas")
}

data class LibraryGroup(val name: String, val subtitle: String, val tracks: List<Track>)

data class LibraryUiState(
    val loading: Boolean = true,
    val tab: LibraryTab = LibraryTab.SONGS,
    val query: String = "",
    /** true si la biblioteca está vacía de verdad (no por el filtro de búsqueda). */
    val empty: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val artists: List<LibraryGroup> = emptyList(),
    val albums: List<LibraryGroup> = emptyList(),
    val folders: List<LibraryGroup> = emptyList(),
    /** Grupo abierto (un artista, álbum o carpeta), o null si se ve la lista de grupos. */
    val openGroup: LibraryGroup? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(private val library: MediaLibrary) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    private var all: List<Track> = emptyList()
    private var job: Job? = null

    /**
     * Llamar cuando el permiso de lectura esté concedido. Cada llamada (al entrar en la
     * biblioteca) vuelve a mirar las carpetas añadidas; la suscripción solo se crea una vez.
     */
    fun start() {
        library.refreshFolders()
        if (job != null) return
        job = viewModelScope.launch {
            library.observe().collect { tracks ->
                all = tracks
                rebuild(loading = false)
            }
        }
    }

    fun setQuery(text: String) {
        _state.update { it.copy(query = text) }
        rebuild()
    }

    fun selectTab(tab: LibraryTab) = _state.update { it.copy(tab = tab, openGroup = null) }
    fun openGroup(group: LibraryGroup) = _state.update { it.copy(openGroup = group) }
    fun closeGroup() = _state.update { it.copy(openGroup = null) }

    /**
     * Borra la canción del dispositivo. Devuelve un IntentSender si Android pide confirmación
     * al usuario; la lista se actualiza sola cuando el archivo desaparece.
     */
    suspend fun delete(track: Track): IntentSender? =
        runCatching { library.delete(track) }.getOrNull()

    /** Recalcula las listas visibles a partir de [all] y del texto de búsqueda. */
    private fun rebuild(loading: Boolean? = null) = _state.update { old ->
        val q = old.query.trim()
        val tracks = if (q.isEmpty()) all else all.filter {
            it.title.contains(q, ignoreCase = true) ||
                    it.artist.contains(q, ignoreCase = true) ||
                    it.album.contains(q, ignoreCase = true)
        }
        val artists = tracks.grouped({ it.artist }) { "${it.size} canciones" }
        val albums = tracks.grouped({ it.album }) { it.first().artist }
        val folders = tracks.grouped({ it.folder }) { "${it.size} canciones" }
        old.copy(
            loading = loading ?: old.loading,
            empty = all.isEmpty(),
            tracks = tracks,
            artists = artists,
            albums = albums,
            folders = folders,
            // Mantiene abierto el grupo actual con su contenido actualizado.
            openGroup = old.openGroup?.let { open ->
                (artists + albums + folders).firstOrNull { it.name == open.name }
            },
        )
    }

    private fun List<Track>.grouped(
        key: (Track) -> String,
        subtitle: (List<Track>) -> String,
    ): List<LibraryGroup> = groupBy(key)
        .map { (name, items) -> LibraryGroup(name, subtitle(items), items) }
        .sortedBy { it.name.lowercase() }
}