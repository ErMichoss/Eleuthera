package com.tuapp.eleuthera.ui.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.eleuthera.data.metadata.EmbeddedLyricsReader
import com.tuapp.eleuthera.data.settings.LoadedSkin
import com.tuapp.eleuthera.data.settings.ProgressSkinRepository
import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.playback.PlayerController
import com.tuapp.eleuthera.playback.PlayerState
import com.tuapp.eleuthera.ui.common.NowPlaying
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LyricsState(
    val text: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val player: PlayerController,
    skins: ProgressSkinRepository,
    private val lyricsReader: EmbeddedLyricsReader,
) : ViewModel() {

    /** Diseño de la barra de progreso; null = la barra normal. */
    val progressSkin: StateFlow<LoadedSkin?> =
        skins.active.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<PlayerState> = player.state
    private val _lyrics = MutableStateFlow(LyricsState())
    val lyrics: StateFlow<LyricsState> = _lyrics.asStateFlow()

    /** Solo qué suena y si está en pausa: las listas no se recomponen por otros cambios. */
    val nowPlaying: StateFlow<NowPlaying> = player.state
        .map { NowPlaying(it.mediaId, it.isPlaying) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, NowPlaying())

    /** Posición actual; solo se consulta mientras alguna pantalla la esté mostrando. */
    val positionMs: StateFlow<Long> = flow {
        while (true) {
            emit(player.positionMs)
            delay(500)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), 0L)

    init {
        viewModelScope.launch {
            state.map { it.mediaUri }
                .distinctUntilChanged()
                .collectLatest { mediaUri ->
                    if (mediaUri.isNullOrBlank()) {
                        _lyrics.value = LyricsState()
                        return@collectLatest
                    }
                    _lyrics.value = LyricsState(loading = true)
                    var readError: Exception? = null
                    try {
                        val embeddedLyrics = lyricsReader.read(Uri.parse(mediaUri))
                            ?.takeIf(String::isNotBlank)
                        if (embeddedLyrics != null) {
                            _lyrics.value = LyricsState(text = embeddedLyrics)
                            return@collectLatest
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        readError = e
                    }
                    _lyrics.value = LyricsState(
                        error = readError?.let {
                            it.message ?: "No se pudieron leer las letras de la canción."
                        },
                    )
                }
        }
    }

    fun play(tracks: List<Track>, index: Int) = player.play(tracks, index)
    fun playShuffled(tracks: List<Track>) = player.playShuffled(tracks)
    fun playNext(track: Track) = player.playNext(track)
    fun addToQueue(track: Track) = player.addToQueue(track)
    fun playNextAll(tracks: List<Track>) = player.playNext(tracks)
    fun addAllToQueue(tracks: List<Track>) = player.addToQueue(tracks)
    fun updateTrack(oldTrackId: Long, updatedTrack: Track) =
        player.updateTrack(oldTrackId, updatedTrack)

    fun toggle() = player.toggle()
    fun next() = player.next()
    fun previous() = player.previous()
    fun seekTo(ms: Long) = player.seekTo(ms)
    fun toggleShuffle() = player.toggleShuffle()
    fun cycleRepeat() = player.cycleRepeat()

    fun jumpTo(index: Int) = player.jumpTo(index)
    fun move(from: Int, to: Int) = player.move(from, to)
    fun remove(index: Int) = player.removeAt(index)
}