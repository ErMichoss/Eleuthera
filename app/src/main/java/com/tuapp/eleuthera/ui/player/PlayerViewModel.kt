package com.tuapp.eleuthera.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.playback.PlayerController
import com.tuapp.eleuthera.playback.PlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class PlayerViewModel @Inject constructor(private val player: PlayerController) : ViewModel() {

    val state: StateFlow<PlayerState> = player.state

    /** Posición actual; solo se consulta mientras alguna pantalla la esté mostrando. */
    val positionMs: StateFlow<Long> = flow {
        while (true) {
            emit(player.positionMs)
            delay(500)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), 0L)

    fun play(tracks: List<Track>, index: Int) = player.play(tracks, index)
    fun playNext(track: Track) = player.playNext(track)
    fun addToQueue(track: Track) = player.addToQueue(track)

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
