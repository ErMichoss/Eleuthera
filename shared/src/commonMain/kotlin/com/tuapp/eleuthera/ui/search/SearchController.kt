package com.tuapp.eleuthera.ui.search

import com.tuapp.eleuthera.data.playlist.PlaylistStore
import com.tuapp.eleuthera.data.youtube.Resolved
import com.tuapp.eleuthera.domain.model.DownloadStatus
import com.tuapp.eleuthera.domain.model.RemoteTrack
import com.tuapp.eleuthera.domain.model.Track
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    /**
     * La canción ya descargada del vídeo [videoId], o null si no está. Si el archivo acaba de
     * aparecer y la biblioteca aún no lo ha leído, espera como mucho [waitMs].
     */
    suspend fun trackFor(videoId: String, waitMs: Long): Track?
}

/** Qué hacer con una canción en cuanto esté descargada. */
enum class AfterDownload(val label: String) {
    PLAY("Reproducir"),
    PLAY_NEXT("Reproducir a continuación"),
    ADD_TO_QUEUE("Añadir a la cola"),
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

    /** Lo que hay que hacer con cada vídeo cuando termine de descargarse. */
    private val pending = HashMap<String, AfterDownload>()

    private val _ready = MutableSharedFlow<Pair<AfterDownload, Track>>(extraBufferCapacity = 16)
    /** Canciones listas para [AfterDownload]: la pantalla se las pasa al reproductor. */
    val ready: SharedFlow<Pair<AfterDownload, Track>> = _ready.asSharedFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Avisos breves para el usuario ("Se añadirá a … al terminar"). */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        // Las descargas viven en su cola (WorkManager en Android): aquí solo se refleja su estado.
        scope.launch {
            backend.downloads().collect { map ->
                _state.update { it.copy(downloads = map) }
                resolvePending(map)
            }
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

    /**
     * Descarga [track] y, al terminar, hace [action] (reproducir, a continuación, a la cola).
     * Si ya estaba descargada, la hace al momento.
     */
    fun downloadThen(track: RemoteTrack, action: AfterDownload) {
        scope.launch {
            backend.trackFor(track.id, waitMs = 0)?.let {
                _ready.emit(action to it)
                return@launch
            }
            pending[track.id] = action
            val s = _state.value
            backend.enqueue(track, s.playlistTitle, s.results.indexOf(track).coerceAtLeast(0))
            _messages.emit("${action.label}: en cuanto termine la descarga")
        }
    }

    /**
     * Descarga [track] y la añade al final de la playlist [playlist] (se crea si no existe).
     * Si ya estaba descargada, solo se añade.
     */
    fun downloadInto(track: RemoteTrack, playlist: String) {
        val name = playlist.trim()
        if (name.isEmpty()) return
        scope.launch {
            val already = backend.trackFor(track.id, waitMs = 0) != null
            backend.enqueue(track, name, PlaylistStore.APPEND)
            _messages.emit(if (already) "Añadida a $name" else "Se añadirá a $name al terminar la descarga")
        }
    }

    /** Lo mismo que [downloadInto] para todos los resultados, en su orden. */
    fun downloadAllInto(playlist: String) {
        val name = playlist.trim()
        if (name.isEmpty()) return
        val tracks = _state.value.results
        scope.launch {
            tracks.forEach { backend.enqueue(it, name, PlaylistStore.APPEND) }
            _messages.emit("${tracks.size} canciones irán a $name")
        }
    }

    private fun resolvePending(map: Map<String, DownloadStatus>) {
        val done = pending.filterKeys { map[it] == DownloadStatus.Done }
        // Si falla, se olvida: el usuario verá el error y puede reintentar.
        pending.keys.removeAll { map[it] is DownloadStatus.Failed }
        done.forEach { (videoId, action) ->
            pending.remove(videoId)
            scope.launch {
                val track = backend.trackFor(videoId, waitMs = 20_000)
                if (track != null) _ready.emit(action to track)
                else _messages.emit("Descargada, pero aún no aparece en la biblioteca")
            }
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