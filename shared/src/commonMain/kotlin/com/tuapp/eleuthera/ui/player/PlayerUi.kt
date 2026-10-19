package com.tuapp.eleuthera.ui.player

import com.tuapp.eleuthera.data.settings.LoadedSkin
import com.tuapp.eleuthera.playback.PlayerState
import kotlinx.coroutines.flow.StateFlow

/**
 * Lo que necesitan del reproductor las pantallas comunes (MiniPlayer y NowPlayingScreen).
 * En Android lo implementa PlayerViewModel; en escritorio, DesktopPlayerUi.
 */
interface PlayerUi {
    val state: StateFlow<PlayerState>

    /** Posición actual, actualizada cada medio segundo mientras alguien la mira. */
    val positionMs: StateFlow<Long>

    /** Diseño de la barra de progreso; null = la barra normal. */
    val progressSkin: StateFlow<LoadedSkin?>

    fun toggle()
    fun next()
    fun previous()
    fun seekTo(ms: Long)
    fun toggleShuffle()
    fun cycleRepeat()
    fun jumpTo(index: Int)
    fun move(from: Int, to: Int)
    fun remove(index: Int)
}