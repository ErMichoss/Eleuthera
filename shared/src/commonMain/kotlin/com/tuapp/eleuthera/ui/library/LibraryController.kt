package com.tuapp.eleuthera.ui.library

import com.tuapp.eleuthera.domain.model.Track
import java.text.Normalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class LibraryTab(val label: String) {
    SONGS("Canciones"), ARTISTS("Artistas"), ALBUMS("Álbumes"), FOLDERS("Carpetas")
}

data class LibraryGroup(val name: String, val subtitle: String, val tracks: List<Track>)

data class FolderLyricsProgress(
    val running: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val found: Int = 0,
    val skipped: Int = 0,
    val notFound: Int = 0,
    val failed: Int = 0,
    val currentTitle: String? = null,
    val message: String? = null,
    val failures: List<String> = emptyList(),
)

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

/**
 * Lo que se ve en la biblioteca: búsqueda, pestañas y grupos. Es igual en las dos
 * plataformas; cada una le pasa sus canciones con [setTracks] (MediaLibrary en Android,
 * DesktopLibrary en escritorio).
 */
class LibraryController {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    /** Cada canción con su texto de búsqueda ya preparado (minúsculas y sin tildes). */
    private var all: List<Pair<Track, String>> = emptyList()

    fun setTracks(tracks: List<Track>) {
        all = tracks.map { it to searchable("${it.title} ${it.artist} ${it.album}") }
        rebuild(loading = false)
    }

    fun setQuery(text: String) {
        _state.update { it.copy(query = text) }
        rebuild()
    }

    fun selectTab(tab: LibraryTab) = _state.update { it.copy(tab = tab, openGroup = null) }
    fun openGroup(group: LibraryGroup) = _state.update { it.copy(openGroup = group) }
    fun closeGroup() = _state.update { it.copy(openGroup = null) }

    /** Recalcula las listas visibles a partir de [all] y del texto de búsqueda. */
    private fun rebuild(loading: Boolean? = null) = _state.update { old ->
        // Cada palabra tiene que aparecer en algún sitio, sin importar tildes ni mayúsculas.
        val words = searchable(old.query).split(' ').filter { it.isNotEmpty() }
        val tracks =
            if (words.isEmpty()) all.map { it.first }
            else all.filter { (_, text) -> words.all { it in text } }.map { it.first }
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

    private companion object {
        val DIACRITICS = Regex("\\p{Mn}+")

        fun searchable(text: String): String =
            Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")
    }
}