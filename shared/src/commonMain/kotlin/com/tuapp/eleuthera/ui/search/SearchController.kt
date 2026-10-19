package com.tuapp.eleuthera.ui.search

import com.tuapp.eleuthera.data.youtube.Resolved
import com.tuapp.eleuthera.domain.model.DownloadStatus
import com.tuapp.eleuthera.domain.model.RemoteTrack
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Lo que Buscar necesita de cada plataforma: resolver búsquedas y enlaces con yt-dlp y
 * mandar canciones a la cola de descargas. En Android lo hacen YtDlpEngine y
 * DownloadScheduler (WorkManager); en escritorio, DesktopYtDlp y DesktopDownloads.
 */
interface SearchBackend {
    /** Acepta búsquedas (ytsearchN:...), vídeos y playlists. */
    suspend fun resolve(target: String): Resolved

    /** Estado de cada descarga, por id de vídeo. */
    fun downloads(): Flow<Map<String, DownloadStatus>>

    /** Pide la canción; [playlist] e [index] la colocan en su playlist al terminar. */
    suspend fun enqueue(track: RemoteTrack, playlist: String?, index: Int)
    fun cancel(videoId: String)
}

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<RemoteTrack> = emptyList(),
    /** Nombre de la playlist si el resultado viene de un enlace de playlist. */
    val playlistTitle: String? = null,
    val downloads: Map<String, DownloadStatus> = emptyMap(),
    val error: String? = null,
)

/** Buscar en YouTube o pegar un enlace, y descargar. Igual en Android y escritorio. */
class SearchController(private val scope: CoroutineScope, private val backend: SearchBackend) {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        // Las descargas viven en su cola (WorkManager en Android): aquí solo se refleja su estado.
        scope.launch {
            backend.downloads().collect { map -> _state.update { it.copy(downloads = map) } }
        }
    }

    fun onQueryChange(text: String) = _state.update { it.copy(query = text) }

    /** Si el texto es un enlace se resuelve tal cual; si no, se busca en YouTube. */
    fun submit() {
        val text = _state.value.query.trim()
        if (text.isEmpty()) return
        searchJob?.cancel()
        searchJob = scope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val resolved =
                    if (text.startsWith("http")) backend.resolve(text)
                    else backend.resolve("ytsearch20:$text")
                _state.update {
                    it.copy(
                        loading = false,
                        results = resolved.tracks,
                        playlistTitle = resolved.playlistTitle,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }

    fun download(track: RemoteTrack) {
        val s = _state.value
        scope.launch {
            backend.enqueue(track, s.playlistTitle, s.results.indexOf(track).coerceAtLeast(0))
        }
    }

    fun downloadAll() {
        val s = _state.value
        scope.launch {
            s.results.forEachIndexed { i, track -> backend.enqueue(track, s.playlistTitle, i) }
        }
    }

    fun cancel(track: RemoteTrack) = backend.cancel(track.id)
}