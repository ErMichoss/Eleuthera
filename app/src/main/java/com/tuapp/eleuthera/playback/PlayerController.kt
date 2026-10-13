package com.tuapp.eleuthera.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.tuapp.eleuthera.data.settings.ThemeRepository
import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.widget.MusicWidgetProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

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
    val title: String = "",
    val artist: String = "",
    /** Carátula incrustada en el MP3 (la miniatura del vídeo), si existe. */
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
) {
    val hasMedia get() = mediaId != null
}

/**
 * Fachada del reproductor para la interfaz. Habla con [PlaybackService] a través de un
 * MediaController; la UI nunca toca ExoPlayer. Todos los métodos se llaman desde el hilo principal.
 *
 * Cómo se organiza la cola (de arriba abajo en el reproductor):
 *   [ya escuchadas] · canción actual · cola del usuario · cola por defecto
 *
 * - Al tocar una canción, la cola por defecto es el resto de esa lista: desde la canción
 *   hacia abajo, o la lista entera barajada si el aleatorio está activo.
 * - El aleatorio y la repetición los gestiona esta clase (no Media3), para que la cola que
 *   se ve sea exactamente el orden en que van a sonar.
 * - Con "repetir playlist", al llegar a la última canción se genera otra vuelta de la lista.
 */
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val themeRepository: ThemeRepository,
) {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var controller: MediaController? = null

    /** Lista desde la que se empezó a reproducir: sirve para regenerar la cola por defecto. */
    private var context: List<Track> = emptyList()
    private var shuffle = false
    private var repeat = RepeatMode.OFF

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_TIMELINE_CHANGED)
            ) {
                ensureNextCycle()
                trimHistory()
            }
            sync(player)
        }
    }

    /** Acciones pedidas antes de conectar con el reproductor (p. ej. desde el widget). */
    private val pendingActions = mutableListOf<() -> Unit>()

    init {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.onSuccess {
                controller = it
                it.shuffleModeEnabled = false   // el aleatorio lo hace esta clase
                it.addListener(listener)
                sync(it)
                pendingActions.forEach { action -> action() }
                pendingActions.clear()
            }
        }, ContextCompat.getMainExecutor(ctx))

        // Widget: se redibuja solo cuando cambia algo que muestra (no en cada evento del
        // reproductor, que llegan varias veces por segundo) o cuando cambia el tema.
        CoroutineScope(Dispatchers.Main).launch {
            combine(_state, themeRepository.config) { s, theme -> s to theme }
                .distinctUntilChangedBy { (s, theme) ->
                    listOf(s.mediaId, s.title, s.artist, s.isPlaying, s.shuffle, s.repeat,
                        s.artwork?.size, theme)
                }
                .collect { (s, theme) -> MusicWidgetProvider.updateWidget(ctx, s, theme) }
        }
    }

    /** Ejecuta [action] en cuanto el reproductor esté conectado (al momento si ya lo está). */
    fun runWhenReady(action: () -> Unit) {
        if (controller != null) action() else pendingActions += action
    }

    private fun sync(p: Player) {
        val meta = p.mediaMetadata
        val current = p.currentMediaItemIndex
        val seen = HashMap<String, Int>()   // repeticiones de cada canción, para las claves
        val upcoming = (current + 1 until p.mediaItemCount).map { i ->
            val item = p.getMediaItemAt(i)
            val occurrence = seen.merge(item.mediaId, 1, Int::plus) ?: 1
            QueueItem(
                key = "${item.mediaId}#$occurrence",
                index = i,
                mediaId = item.mediaId,
                title = item.mediaMetadata.title?.toString().orEmpty(),
                artist = item.mediaMetadata.artist?.toString().orEmpty(),
                userQueued = item.isUserQueued,
            )
        }
        _state.value = PlayerState(
            mediaId = p.currentMediaItem?.mediaId,
            title = meta.title?.toString().orEmpty(),
            artist = meta.artist?.toString().orEmpty(),
            artwork = meta.artworkData,
            currentIndex = current,
            userQueue = upcoming.filter { it.userQueued },
            upNext = upcoming.filterNot { it.userQueued },
            isPlaying = p.isPlaying,
            shuffle = shuffle,
            repeat = repeat,
            durationMs = p.duration.coerceAtLeast(0),
        )
    }

    val positionMs: Long get() = controller?.currentPosition ?: 0L

    // ---- Reproducción ----

    /**
     * Empieza a sonar [tracks][startIndex]. La cola por defecto pasa a ser el resto de esa
     * lista; la cola del usuario que hubiera pendiente se conserva y suena primero.
     */
    fun play(tracks: List<Track>, startIndex: Int = 0) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        val start = startIndex.coerceIn(tracks.indices)
        context = tracks

        val pendingUser = upcomingIndices(c).filter { c.getMediaItemAt(it).isUserQueued }
            .map { c.getMediaItemAt(it) }
        val rest = if (shuffle) tracks.filterIndexed { i, _ -> i != start }.shuffled()
        else tracks.drop(start + 1)

        val items = listOf(tracks[start].toMediaItem()) + pendingUser + rest.map { it.toMediaItem() }
        c.setMediaItems(items, 0, 0L)
        applyRepeatToPlayer(c)
        c.prepare()
        c.play()
    }

    /** Botón "Aleatorio" de una playlist: activa el aleatorio y empieza por una canción al azar. */
    fun playShuffled(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        shuffle = true
        play(tracks, tracks.indices.random())
    }

    fun toggle() { controller?.run { if (isPlaying) pause() else play() } }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPrevious() }   // <3 s: canción anterior; si no, reinicia
    fun seekTo(ms: Long) { controller?.seekTo(ms) }

    /** Al cambiar el aleatorio se regenera la cola por defecto; la del usuario no se toca. */
    fun toggleShuffle() {
        shuffle = !shuffle
        val c = controller ?: return
        if (context.isNotEmpty() && c.mediaItemCount > 0) {
            removeDefaultUpcoming(c)
            val currentId = c.currentMediaItem?.mediaId
            val rest = if (shuffle) {
                context.filter { it.id.toString() != currentId }.shuffled()
            } else {
                // Sin aleatorio: desde la canción actual hacia abajo en la playlist.
                val pos = context.indexOfFirst { it.id.toString() == currentId }
                if (pos >= 0) context.drop(pos + 1) else context
            }
            c.addMediaItems(c.mediaItemCount, rest.map { it.toMediaItem() })
        }
        sync(c)
    }

    /** Desactivado → repetir playlist → repetir canción → desactivado. */
    fun cycleRepeat() {
        repeat = when (repeat) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        val c = controller ?: return
        applyRepeatToPlayer(c)
        ensureNextCycle()
        sync(c)
    }

    // ---- Cola ----

    /** Primera de la cola del usuario: suena justo después de la actual. */
    fun playNext(track: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(listOf(track))
        c.addMediaItem(c.currentMediaItemIndex + 1, track.toMediaItem(userQueued = true))
    }

    /** Al final de la cola del usuario, pero antes que el resto de la playlist. */
    fun addToQueue(track: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(listOf(track))
        var pos = c.currentMediaItemIndex + 1
        while (pos < c.mediaItemCount && c.getMediaItemAt(pos).isUserQueued) pos++
        c.addMediaItem(pos, track.toMediaItem(userQueued = true))
    }

    /** Mueve una canción pendiente; [from] y [to] son posiciones reales del reproductor. */
    fun move(from: Int, to: Int) {
        val c = controller ?: return
        val valid = (c.currentMediaItemIndex + 1) until c.mediaItemCount
        if (from in valid && to in valid && from != to) c.moveMediaItem(from, to)
    }

    fun removeAt(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount && index != c.currentMediaItemIndex) {
            c.removeMediaItem(index)
        }
    }

    fun jumpTo(index: Int) {
        controller?.run {
            seekToDefaultPosition(index)
            play()
        }
    }

    // ---- Internos ----

    /** Media3 solo se encarga de "repetir canción"; "repetir playlist" lo hace [ensureNextCycle]. */
    private fun applyRepeatToPlayer(c: Player) {
        c.repeatMode = if (repeat == RepeatMode.ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    /** Con "repetir playlist", al llegar a la última canción se añade otra vuelta de la lista. */
    private fun ensureNextCycle() {
        val c = controller ?: return
        if (repeat != RepeatMode.ALL || context.isEmpty() || c.mediaItemCount == 0) return
        if (c.currentMediaItemIndex < c.mediaItemCount - 1) return
        val cycle = if (shuffle) context.shuffled() else context
        c.addMediaItems(c.mediaItemCount, cycle.map { it.toMediaItem() })
    }

    /** Evita que el historial de canciones ya escuchadas crezca sin límite. */
    private fun trimHistory() {
        val c = controller ?: return
        val current = c.currentMediaItemIndex
        if (current > MAX_HISTORY + 20) c.removeMediaItems(0, current - MAX_HISTORY)
    }

    private fun upcomingIndices(c: Player): IntRange = (c.currentMediaItemIndex + 1) until c.mediaItemCount

    /** Quita la cola por defecto pendiente, por tramos contiguos y empezando por el final. */
    private fun removeDefaultUpcoming(c: Player) {
        val indices = upcomingIndices(c).filterNot { c.getMediaItemAt(it).isUserQueued }
        if (indices.isEmpty()) return
        var end = indices.last() + 1
        var start = indices.last()
        for (i in indices.reversed().drop(1)) {
            if (i == start - 1) {
                start = i
            } else {
                c.removeMediaItems(start, end)
                start = i
                end = i + 1
            }
        }
        c.removeMediaItems(start, end)
    }

    private companion object { const val MAX_HISTORY = 50 }
}

private const val EXTRA_USER_QUEUED = "eleuthera.user_queued"

private val MediaItem.isUserQueued: Boolean
    get() = mediaMetadata.extras?.getBoolean(EXTRA_USER_QUEUED) == true

fun Track.toMediaItem(userQueued: Boolean = false): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setExtras(Bundle().apply { putBoolean(EXTRA_USER_QUEUED, userQueued) })
            .build()
    )
    .build()