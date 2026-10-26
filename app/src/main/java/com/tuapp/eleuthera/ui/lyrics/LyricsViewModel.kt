package com.tuapp.eleuthera.ui.lyrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.playback.PlayerController
import com.tuapp.eleuthera.ui.library.LibraryGroup
import com.tuapp.eleuthera.ui.lyrics.LyricsController
import com.tuapp.eleuthera.ui.lyrics.LyricsTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class LyricsUiState(
    val text: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val subtitleMessage: String? = null,
    val mediaUri: String? = null,
)

/** Android wrapper around the shared controller; owns player association and folder UI state. */
@HiltViewModel
class LyricsViewModel @Inject constructor(
    val controller: LyricsController,
    player: PlayerController,
) : ViewModel() {

    private val _lyrics = MutableStateFlow(LyricsUiState())
    val lyrics: StateFlow<LyricsUiState> = _lyrics.asStateFlow()
    val batchProgress = controller.batchProgress

    init {
        viewModelScope.launch {
            // collectLatest cancela la consulta anterior al cambiar de pista; lookup también
            // comprueba la URI antes de persistir, por si el resultado ya estaba en vuelo.
            player.state
                .map { LyricsTrack(it.mediaUri.orEmpty(), it.title, it.artist, it.durationMs) }
                .distinctUntilChanged()
                .collectLatest { track ->
                    if (track.uri.isBlank()) {
                        _lyrics.value = LyricsUiState()
                        return@collectLatest
                    }
                    _lyrics.value = LyricsUiState(loading = true, mediaUri = track.uri)
                    try {
                        val result = controller.lookup(track) {
                            player.state.value.mediaUri == track.uri
                        }
                        if (player.state.value.mediaUri == track.uri) {
                            val unsyncedMessage =
                                if (!result.fromLocal && result.text != null &&
                                    result.hasSyncedLyrics == false
                                ) {
                                    "LRCLIB solo devolvió letras sin marcas de tiempo; " +
                                        "no se pueden sincronizar como subtítulos."
                                } else {
                                    null
                                }
                            _lyrics.value = LyricsUiState(
                                text = result.text,
                                mediaUri = track.uri,
                                subtitleMessage = result.subtitleMessage ?: unsyncedMessage,
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (player.state.value.mediaUri == track.uri) {
                            _lyrics.value = LyricsUiState(
                                mediaUri = track.uri,
                                error = e.message ?: "No se pudieron leer las letras de la canción.",
                            )
                        }
                    }
                }
        }
    }

    /** Convierte las pistas de una carpeta al modelo común, quitando URI duplicadas. */
    fun toggleFolderSearch(group: LibraryGroup) {
        controller.toggleBatch(
            key = group.name,
            tracks = group.tracks.distinctBy { it.uri.toString() }.map { it.toLyricsTrack() },
        )
    }
}

private fun Track.toLyricsTrack() = LyricsTrack(
    uri = uri.toString(),
    title = title,
    artist = artist,
    durationMs = durationMs,
)
