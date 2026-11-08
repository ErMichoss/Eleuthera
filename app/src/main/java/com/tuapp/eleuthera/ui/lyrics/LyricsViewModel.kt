package com.tuapp.eleuthera.ui.lyrics

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.eleuthera.data.lrclib.LrclibHttpException
import com.tuapp.eleuthera.data.lrclib.LrclibLyrics
import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.playback.PlayerController
import com.tuapp.eleuthera.ui.library.LibraryGroup
import com.tuapp.eleuthera.ui.lyrics.LyricsController
import com.tuapp.eleuthera.ui.lyrics.LyricsTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    val candidates: List<LrclibLyrics> = emptyList(),
    val searchingCandidates: Boolean = false,
    val candidateSearchError: String? = null,
    val trackTitle: String = "",
    val trackArtist: String = "",
)

/** Android wrapper around the shared controller; owns player association and folder UI state. */
@HiltViewModel
class LyricsViewModel @Inject constructor(
    val controller: LyricsController,
    private val player: PlayerController,
) : ViewModel() {

    private val _lyrics = MutableStateFlow(LyricsUiState())
    val lyrics: StateFlow<LyricsUiState> = _lyrics.asStateFlow()
    val batchProgress = controller.batchProgress
    private var currentTrack: LyricsTrack? = null
    private var candidateJob: Job? = null
    private var candidateSearchAllowedAtMs = 0L
    private var serverSearchAllowedAtMs = 0L
    private var serverCooldownMessage: String? = null

    init {
        viewModelScope.launch {
            // collectLatest cancela la consulta anterior al cambiar de pista; lookup también
            // comprueba la URI antes de persistir, por si el resultado ya estaba en vuelo.
            player.state
                .map { LyricsTrack(it.mediaUri.orEmpty(), it.title, it.artist, it.durationMs) }
                .distinctUntilChanged()
                .collectLatest { track ->
                    currentTrack = track.takeIf { it.uri.isNotBlank() }
                    candidateJob?.cancel()
                    if (track.uri.isBlank()) {
                        _lyrics.value = LyricsUiState()
                        return@collectLatest
                    }
                    _lyrics.value = LyricsUiState(
                        loading = true,
                        mediaUri = track.uri,
                        trackTitle = track.title,
                        trackArtist = track.artist,
                    )
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
                                trackTitle = track.title,
                                trackArtist = track.artist,
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (player.state.value.mediaUri == track.uri) {
                            _lyrics.value = LyricsUiState(
                                mediaUri = track.uri,
                                error = e.message ?: "No se pudieron leer las letras de la canción.",
                                trackTitle = track.title,
                                trackArtist = track.artist,
                            )
                        }
                    }
                }
        }
    }

    fun searchMoreCandidates() {
        val track = currentTrack ?: return
        startCandidateSearch(track, track.title, track.artist)
    }

    fun searchManualCandidates(title: String, artist: String) {
        val track = currentTrack ?: return
        if (title.isBlank()) {
            _lyrics.value = _lyrics.value.copy(candidateSearchError = "Escribe el título de la canción.")
            return
        }
        startCandidateSearch(track, title.trim(), artist.trim())
    }

    fun updateLyricsIfCurrent(uri: String, text: String) {
        if (player.state.value.mediaUri != uri || _lyrics.value.mediaUri != uri) return
        _lyrics.value = _lyrics.value.copy(
            text = text,
            subtitleMessage = null,
            error = null,
        )
    }

    private fun startCandidateSearch(track: LyricsTrack, title: String, artist: String) {
        if (!isTrackCurrent(track) || _lyrics.value.loading) return
        val now = SystemClock.elapsedRealtime()
        if (now < serverSearchAllowedAtMs) {
            _lyrics.value = _lyrics.value.copy(
                candidates = emptyList(),
                candidateSearchError = serverCooldownMessage
                    ?: "El servidor está ocupado ahora mismo; inténtalo de nuevo más tarde.",
            )
            return
        }
        if (now < candidateSearchAllowedAtMs) {
            _lyrics.value = _lyrics.value.copy(
                candidates = emptyList(),
                candidateSearchError = "Espera un momento antes de volver a buscar otras coincidencias.",
            )
            return
        }
        candidateSearchAllowedAtMs = now + SEARCH_COOLDOWN_MS
        candidateJob?.cancel()
        _lyrics.value = _lyrics.value.copy(
            candidates = emptyList(),
            searchingCandidates = true,
            candidateSearchError = null,
        )
        candidateJob = viewModelScope.launch {
            try {
                val candidates = controller.searchCandidates(track, title, artist)
                if (isTrackCurrent(track)) {
                    _lyrics.value = _lyrics.value.copy(
                        candidates = candidates,
                        searchingCandidates = false,
                        candidateSearchError = if (candidates.isEmpty()) {
                            "No se encontraron alternativas. Prueba a revisar el título o el artista."
                        } else {
                            null
                        },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is LrclibHttpException && e.statusCode in RATE_LIMITED_STATUS_CODES) {
                    val retryDelayMs = maxOf(e.retryAfterMs ?: SERVER_ERROR_COOLDOWN_MS, SEARCH_COOLDOWN_MS)
                    serverSearchAllowedAtMs = SystemClock.elapsedRealtime() + retryDelayMs
                    serverCooldownMessage = e.toManualSearchMessage()
                }
                if (isTrackCurrent(track)) {
                    _lyrics.value = _lyrics.value.copy(
                        candidates = emptyList(),
                        searchingCandidates = false,
                        candidateSearchError = e.toManualSearchMessage(),
                    )
                }
            }
        }
    }

    fun selectCandidate(candidate: LrclibLyrics) {
        val track = currentTrack ?: return
        if (!isTrackCurrent(track)) return
        candidateJob?.cancel()
        _lyrics.value = _lyrics.value.copy(
            searchingCandidates = true,
            candidateSearchError = null,
        )
        candidateJob = viewModelScope.launch {
            try {
                val result = controller.saveCandidate(track, candidate) { isTrackCurrent(track) }
                if (isTrackCurrent(track)) {
                    val unsyncedMessage =
                        if (result.text != null && result.hasSyncedLyrics == false) {
                            "La opción elegida solo tiene letras sin marcas de tiempo; " +
                                "no se pueden sincronizar como subtítulos."
                        } else {
                            null
                        }
                    _lyrics.value = _lyrics.value.copy(
                        text = result.text,
                        subtitleMessage = result.subtitleMessage ?: unsyncedMessage,
                        candidates = emptyList(),
                        searchingCandidates = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isTrackCurrent(track)) {
                    _lyrics.value = _lyrics.value.copy(
                        searchingCandidates = false,
                        candidateSearchError = e.message
                            ?: "No se pudieron guardar las letras seleccionadas.",
                    )
                }
            }
        }
    }

    private fun isTrackCurrent(track: LyricsTrack): Boolean =
        currentTrack?.uri == track.uri &&
            _lyrics.value.mediaUri == track.uri &&
            player.state.value.mediaUri == track.uri

    private fun Exception.toManualSearchMessage(): String = when {
        this is LrclibHttpException && statusCode == 503 ->
            "El servidor está ocupado ahora mismo; inténtalo de nuevo más tarde."
        this is LrclibHttpException && statusCode == 429 ->
            "El servidor está recibiendo demasiadas búsquedas; inténtalo de nuevo más tarde."
        else -> "No se pudo completar la búsqueda de otras letras. Inténtalo de nuevo más tarde."
    }

    /** Convierte las pistas de una carpeta al modelo común, quitando URI duplicadas. */
    fun toggleFolderSearch(group: LibraryGroup) {
        controller.toggleBatch(
            key = group.name,
            tracks = group.tracks.distinctBy { it.uri.toString() }.map { it.toLyricsTrack() },
        )
    }

    private companion object {
        const val SEARCH_COOLDOWN_MS = 5_000L
        const val SERVER_ERROR_COOLDOWN_MS = 30_000L
        val RATE_LIMITED_STATUS_CODES = setOf(429, 503)
    }
}

private fun Track.toLyricsTrack() = LyricsTrack(
    uri = uri.toString(),
    title = title,
    artist = artist,
    durationMs = durationMs,
)
