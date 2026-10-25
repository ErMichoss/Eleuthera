package com.tuapp.eleuthera.ui.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.eleuthera.data.lrclib.LrclibService
import com.tuapp.eleuthera.data.metadata.EmbeddedLyricsReader
import com.tuapp.eleuthera.data.metadata.bestLyricsMatch
import com.tuapp.eleuthera.data.metadata.LyricsSubtitleWriter
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LyricsState(
    val text: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val subtitleMessage: String? = null,
    val mediaUri: String? = null,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val player: PlayerController,
    skins: ProgressSkinRepository,
    private val lyricsReader: EmbeddedLyricsReader,
    private val lrclib: LrclibService,
    private val subtitleWriter: LyricsSubtitleWriter,
) : ViewModel(), PlayerUi {

    /** Diseño de la barra de progreso; null = la barra normal. */
    override val progressSkin: StateFlow<LoadedSkin?> =
        skins.active.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    override val state: StateFlow<PlayerState> = player.state
    val currentMediaUri: StateFlow<String?> = state
        .map { it.mediaUri }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _lyrics = MutableStateFlow(LyricsState())
    val lyrics: StateFlow<LyricsState> = _lyrics.asStateFlow()

    /** Solo qué suena y si está en pausa: las listas no se recomponen por otros cambios. */
    val nowPlaying: StateFlow<NowPlaying> = player.state
        .map { NowPlaying(it.mediaId, it.isPlaying) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, NowPlaying())

    /** Posición actual; solo se consulta mientras alguna pantalla la esté mostrando. */
    override val positionMs: StateFlow<Long> = flow {
        while (true) {
            emit(player.positionMs)
            delay(500)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), 0L)
    init {
        viewModelScope.launch {
            state.map { LyricsRequest(it.mediaUri, it.title, it.artist, it.durationMs) }
                .distinctUntilChangedBy { it.mediaUri }
                .collect { request ->
                    val mediaUri = request.mediaUri
                    if (mediaUri.isNullOrBlank()) {
                        _lyrics.value = LyricsState()
                        return@collect
                    }
                    _lyrics.value = LyricsState(loading = true, mediaUri = mediaUri)
                    var readError: Exception? = null
                    try {
                        val embeddedLyrics = lyricsReader.read(Uri.parse(mediaUri))
                            ?.takeIf(String::isNotBlank)
                        if (embeddedLyrics != null) {
                            if (state.value.mediaUri == mediaUri) {
                                _lyrics.value = LyricsState(text = embeddedLyrics, mediaUri = mediaUri)
                            }
                            return@collect
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        readError = e
                    }

                    val localLyrics = try {
                        subtitleWriter.readLocal(Uri.parse(mediaUri))?.takeIf(String::isNotBlank)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        readError = e
                        null
                    }
                    if (localLyrics != null) {
                        if (state.value.mediaUri == mediaUri) {
                            _lyrics.value = LyricsState(text = localLyrics, mediaUri = mediaUri)
                        }
                        return@collect
                    }

                    var subtitleMessage: String? = null
                    val remoteLyrics = try {
                        if (request.title.isBlank() || request.artist.isBlank()) {
                            null
                        } else {
                            val match = lrclib.searchLyricsByMetadata(
                                trackName = request.title,
                                artistName = request.artist,
                            ).bestLyricsMatch(request.title, request.artist, request.durationMs)
                            val syncedLyrics = match?.syncedLyrics?.takeIf(String::isNotBlank)
                            val plainLyrics = match?.plainLyrics?.takeIf(String::isNotBlank)
                            if (syncedLyrics == null && !match?.plainLyrics.isNullOrBlank()) {
                                subtitleMessage =
                                    "LRCLIB solo devolvió letras sin marcas de tiempo; no se pueden sincronizar como subtítulos."
                            }
                            val lyricsToSave = syncedLyrics ?: plainLyrics
                            lyricsToSave?.let { lyrics ->
                                if (state.value.mediaUri == mediaUri) {
                                    subtitleMessage = try {
                                        subtitleWriter.save(Uri.parse(mediaUri), lyrics) {
                                            state.value.mediaUri == mediaUri
                                        }
                                    } catch (_: LyricsSubtitleWriter.StaleTrackException) {
                                        null
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        "No se pudieron guardar los subtítulos: ${e.message ?: "error de almacenamiento"}."
                                    }
                                }
                            }
                            syncedLyrics ?: plainLyrics
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        readError = e
                        null
                    }

                    if (state.value.mediaUri == mediaUri) {
                        _lyrics.value = LyricsState(
                            text = remoteLyrics,
                            subtitleMessage = subtitleMessage,
                            mediaUri = mediaUri,
                            error = if (remoteLyrics == null) readError?.let {
                                it.message ?: "No se pudieron leer las letras de la canción."
                            } else null,
                        )
                    }
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

    override fun toggle() = player.toggle()
    override fun next() = player.next()
    override fun previous() = player.previous()
    override fun seekTo(ms: Long) = player.seekTo(ms)
    override fun toggleShuffle() = player.toggleShuffle()
    override fun cycleRepeat() = player.cycleRepeat()

    override fun jumpTo(index: Int) = player.jumpTo(index)
    override fun move(from: Int, to: Int) = player.move(from, to)
    override fun remove(index: Int) = player.removeAt(index)
}

private data class LyricsRequest(
    val mediaUri: String?,
    val title: String,
    val artist: String,
    val durationMs: Long,
)
