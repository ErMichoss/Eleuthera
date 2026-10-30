package com.tuapp.eleuthera.playback

import com.tuapp.eleuthera.domain.model.Track
import kotlinx.coroutines.flow.StateFlow

enum class RepeatMode { OFF, ALL, ONE }

/**
 * Una canción pendiente de sonar.
 * [index] es su posición real en el reproductor; [key] es única aunque la canción se repita.
 * [userQueued] = la añadió el usuario con "Añadir a la cola" o "Reproducir a continuación".
 */
data class QueueItem(
    val key: String,
    val index: Int,
    val mediaId: String,
    val title: String,
    val artist: String,
    val userQueued: Boolean,
)

data class PlayerState(
    val mediaId: String? = null,
    val mediaUri: String? = null,
    val title: String = "",
    val artist: String = "",
    /** Carátula incrustada en el archivo (la miniatura del vídeo), si existe. */
    val artwork: ByteArray? = null,
    val currentIndex: Int = 0,
    /** Cola del usuario: suena antes que el resto, en el orden en que se añadió. */
    val userQueue: List<QueueItem> = emptyList(),
    /** Cola por defecto: el resto de la playlist, en orden o barajada. */
    val upNext: List<QueueItem> = emptyList(),
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val durationMs: Long = 0,
    val positionMs: Long = 0,
) {
    val hasMedia get() = mediaId != null
}

/**
 * El reproductor visto desde las pantallas. Android lo implementa con Media3
 * (PlayerController) y escritorio con ffmpeg (DesktopPlayerController); la cola funciona
 * igual en los dos:
 *
 *   [ya escuchadas] · canción actual · cola del usuario · cola por defecto
 *
 * - Al tocar una canción, la cola por defecto es el resto de esa lista: desde la canción
 *   hacia abajo, o la lista entera barajada si el aleatorio está activo.
 * - Con "repetir playlist", al llegar a la última canción se genera otra vuelta de la lista.
 *
 * Todos los métodos se llaman desde el hilo principal.
 */
interface MusicPlayer {
    val state: StateFlow<PlayerState>

    /** Posición exacta ahora mismo (state.positionMs se actualiza solo cada segundo). */
    val positionMs: Long

    /** Empieza a sonar [tracks][startIndex]; el resto de la lista pasa a la cola por defecto. */
    fun play(tracks: List<Track>, startIndex: Int = 0)

    /** Activa el aleatorio y empieza por una canción al azar. */
    fun playShuffled(tracks: List<Track>)

    fun toggle()
    fun next()

    /** Con más de 3 s reproducidos vuelve al principio; si no, a la canción anterior. */
    fun previous()
    fun seekTo(ms: Long)
    fun toggleShuffle()

    /** Desactivado → repetir playlist → repetir canción → desactivado. */
    fun cycleRepeat()

    /** Primeras de la cola del usuario: suenan justo después de la actual. */
    fun playNext(tracks: List<Track>)

    /** Al final de la cola del usuario, pero antes que el resto de la playlist. */
    fun addToQueue(tracks: List<Track>)

    fun playNext(track: Track) = playNext(listOf(track))
    fun addToQueue(track: Track) = addToQueue(listOf(track))

    /** Mueve una canción pendiente; [from] y [to] son posiciones reales del reproductor. */
    fun move(from: Int, to: Int)
    fun removeAt(index: Int)
    fun jumpTo(index: Int)
}