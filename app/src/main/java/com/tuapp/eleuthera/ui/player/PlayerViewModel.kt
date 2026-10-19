package com.tuapp.eleuthera.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.eleuthera.data.settings.LoadedSkin
import com.tuapp.eleuthera.data.settings.ProgressSkinRepository
import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.playback.PlayerController
import com.tuapp.eleuthera.playback.PlayerState
import com.tuapp.eleuthera.ui.common.NowPlaying
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val player: PlayerController,
    skins: ProgressSkinRepository,
) : ViewModel(), PlayerUi {

    /** Diseño de la barra de progreso; null = la barra normal. */
    override val progressSkin: StateFlow<LoadedSkin?> =
        skins.active.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    override val state: StateFlow<PlayerState> = player.state

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

    fun play(tracks: List<Track>, index: Int) = player.play(tracks, index)
    fun playShuffled(tracks: List<Track>) = player.playShuffled(tracks)
    fun playNext(track: Track) = player.playNext(track)
    fun addToQueue(track: Track) = player.addToQueue(track)
    fun playNextAll(tracks: List<Track>) = player.playNext(tracks)
    fun addAllToQueue(tracks: List<Track>) = player.addToQueue(tracks)

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