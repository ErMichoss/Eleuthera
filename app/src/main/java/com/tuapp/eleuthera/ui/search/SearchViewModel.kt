package com.tuapp.eleuthera.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.eleuthera.data.download.DownloadScheduler
import com.tuapp.eleuthera.data.youtube.YtDlpEngine
import com.tuapp.eleuthera.domain.model.DownloadStatus
import com.tuapp.eleuthera.domain.model.RemoteTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<RemoteTrack> = emptyList(),
    /** Nombre de la playlist si el resultado viene de un enlace de playlist. */
    val playlistTitle: String? = null,
    val downloads: Map<String, DownloadStatus> = emptyMap(),
    val error: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val engine: YtDlpEngine,
    private val scheduler: DownloadScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        // Las descargas viven en WorkManager: aquí solo se refleja su estado.
        viewModelScope.launch {
            scheduler.observe().collect { map -> _state.update { it.copy(downloads = map) } }
        }
    }

    fun onQueryChange(text: String) = _state.update { it.copy(query = text) }

    /** Si el texto es un enlace se resuelve tal cual; si no, se busca en YouTube. */
    fun submit() {
        val text = _state.value.query.trim()
        if (text.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val resolved =
                    if (text.startsWith("http")) engine.resolve(text)
                    else engine.resolve("ytsearch20:$text")
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
        scheduler.enqueue(track, s.playlistTitle, s.results.indexOf(track).coerceAtLeast(0))
    }

    fun downloadAll() = scheduler.enqueueAll(_state.value.results, _state.value.playlistTitle)

    fun cancel(track: RemoteTrack) = scheduler.cancel(track.id)
}
