package com.tuapp.eleuthera.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.tuapp.eleuthera.data.settings.LoadedSkin
import com.tuapp.eleuthera.data.settings.ProgressSkinRepository
import com.tuapp.eleuthera.data.settings.ThemeRepository
import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.widget.MusicWidgetProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    private val progressSkinRepository: ProgressSkinRepository,
) : MusicPlayer {

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var controller: MediaController? = null

    /** Lista desde la que se empezó a reproducir: sirve para regenerar la cola por defecto. */
    private var context: List<Track> = emptyList()
    private var shuffle = false
    private var repeat = RepeatMode.OFF

    private var activeSkin: LoadedSkin? = null
    private var tickerJob: Job? = null

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

    private data class PendingAction(
        val action: () -> Unit,
        val onUnavailable: () -> Unit,
        var timeoutJob: Job? = null,
    )

    /** Acciones pedidas antes de conectar con el reproductor (p. ej. desde el widget). */
    private val pendingActions = mutableListOf<PendingAction>()
    private var connectionFailure: Throwable? = null

    init {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            val connected = try {
                future.get()
            } catch (failure: ExecutionException) {
                failPendingActions(failure)
                return@addListener
            } catch (failure: CancellationException) {
                failPendingActions(failure)
                return@addListener
            } catch (failure: InterruptedException) {
                Thread.currentThread().interrupt()
                failPendingActions(failure)
                return@addListener
            }

            controller = connected
            connected.shuffleModeEnabled = false   // el aleatorio lo hace esta clase
            connected.addListener(listener)
            sync(connected)
            val actions = pendingActions.toList()
            pendingActions.clear()
            actions.forEach { pending ->
                pending.timeoutJob?.cancel()
                pending.action()
            }
        }, ContextCompat.getMainExecutor(ctx))

        // Widget: se redibuja solo cuando cambia algo que muestra (no en cada evento del
        // reproductor, que llegan varias veces por segundo) o cuando cambia el tema.
        CoroutineScope(Dispatchers.Main).launch {
            combine(_state, themeRepository.config, progressSkinRepository.active) { s, theme, skin ->
                Triple(s, theme, skin)
            }
                .distinctUntilChangedBy { (s, theme, skin) ->
                    listOf(
                        s.mediaId, s.title, s.artist, s.isPlaying, s.shuffle, s.repeat,
                        s.artwork?.size, s.positionMs / 1000, s.durationMs, theme, skin
                    )
                }
                .collect { (s, theme, skin) ->
                    activeSkin = skin
                    MusicWidgetProvider.updateWidget(ctx, s, theme, skin)
                }
        }
    }

    /** Ejecuta [action] al conectar; descarta la petición y llama [onUnavailable] tras 8 s. */
    fun runWhenReady(action: () -> Unit, onUnavailable: () -> Unit = {}) {
        if (controller != null) {
            action()
            return
        }
        if (connectionFailure != null) {
            onUnavailable()
            return
        }

        val pending = PendingAction(action, onUnavailable)
        pendingActions += pending
        pending.timeoutJob = CoroutineScope(Dispatchers.Main).launch {
            delay(8_000)
            if (pendingActions.remove(pending)) {
                Log.w(TAG, "Discarding a widget action because the player did not connect in time")
                pending.onUnavailable()
            }
        }
    }

    private fun failPendingActions(failure: Exception) {
        connectionFailure = failure
        Log.e(TAG, "Could not connect to the playback service", failure)
        val actions = pendingActions.toList()
        pendingActions.clear()
        actions.forEach { pending ->
            pending.timeoutJob?.cancel()
            pending.onUnavailable()
        }
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
            positionMs = p.currentPosition.coerceAtLeast(0),
        )
        updateTicker(p.isPlaying)
    }

    private fun updateTicker(isPlaying: Boolean) {
        tickerJob?.cancel()
        if (isPlaying) {
            tickerJob = CoroutineScope(Dispatchers.Main).launch {
                while (isActive) {
                    delay(1000)
                    controller?.let { c ->
                        val pos = c.currentPosition.coerceAtLeast(0)
                        _state.value = _state.value.copy(positionMs = pos)
                    }
                }
            }
        }
    }

    override val positionMs: Long get() = controller?.currentPosition ?: 0L

    // ---- Reproducción ----

    /**
     * Empieza a sonar [tracks][startIndex]. La cola por defecto pasa a ser el resto de esa
     * lista; la cola del usuario que hubiera pendiente se conserva y suena primero.
     */
    override fun play(tracks: List<Track>, startIndex: Int) {
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
    override fun playShuffled(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        shuffle = true
        play(tracks, tracks.indices.random())
    }

    override fun toggle() { controller?.run { if (isPlaying) pause() else play() } }
    override fun next() { controller?.seekToNextMediaItem() }
    override fun previous() { controller?.seekToPrevious() }   // <3 s: canción anterior; si no, reinicia
    override fun seekTo(ms: Long) { controller?.seekTo(ms) }

    /** Al cambiar el aleatorio se regenera la cola por defecto; la del usuario no se toca. */
    override fun toggleShuffle() {
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
    override fun cycleRepeat() {
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

    override fun playNext(track: Track) = playNext(listOf(track))
    override fun addToQueue(track: Track) = addToQueue(listOf(track))

    /**
     * Primeras de la cola del usuario: suenan justo después de la actual, en su orden.
     * Sirve para una canción o para una playlist entera.
     */
    override fun playNext(tracks: List<Track>) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        if (c.mediaItemCount == 0) return play(tracks)
        c.addMediaItems(c.currentMediaItemIndex + 1, tracks.map { it.toMediaItem(userQueued = true) })
    }

    /** Al final de la cola del usuario, pero antes que el resto de la playlist. */
    override fun addToQueue(tracks: List<Track>) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        if (c.mediaItemCount == 0) return play(tracks)
        var pos = c.currentMediaItemIndex + 1
        while (pos < c.mediaItemCount && c.getMediaItemAt(pos).isUserQueued) pos++
        c.addMediaItems(pos, tracks.map { it.toMediaItem(userQueued = true) })
    }

    /** Mueve una canción pendiente; [from] y [to] son posiciones reales del reproductor. */
    override fun move(from: Int, to: Int) {
        val c = controller ?: return
        val valid = (c.currentMediaItemIndex + 1) until c.mediaItemCount
        if (from in valid && to in valid && from != to) c.moveMediaItem(from, to)
    }

    override fun removeAt(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount && index != c.currentMediaItemIndex) {
            c.removeMediaItem(index)
        }
    }

    override fun jumpTo(index: Int) {
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

    private companion object {
        const val MAX_HISTORY = 50
        const val TAG = "PlayerController"
    }
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