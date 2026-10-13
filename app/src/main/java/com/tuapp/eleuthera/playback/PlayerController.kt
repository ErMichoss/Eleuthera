package com.tuapp.eleuthera.playback

import android.content.ComponentName
import android.content.Context
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
import kotlinx.coroutines.launch

/** [key] es única dentro de la cola aunque la misma canción aparezca varias veces. */
data class QueueItem(val key: String, val mediaId: String, val title: String, val artist: String)

data class PlayerState(
    val mediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    /** Carátula incrustada en el MP3 (la miniatura del vídeo), si existe. */
    val artwork: ByteArray? = null,
    val queue: List<QueueItem> = emptyList(),
    val index: Int = 0,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val durationMs: Long = 0,
) {
    val hasMedia get() = mediaId != null
}

/**
 * Fachada del reproductor para la interfaz. Habla con [PlaybackService] a través de un
 * MediaController; la UI nunca toca ExoPlayer. Todos los métodos se llaman desde el hilo principal.
 */
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val themeRepository: ThemeRepository,
) {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
    }

    init {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.onSuccess {
                controller = it
                it.addListener(listener)
                sync(it)
            }
        }, ContextCompat.getMainExecutor(ctx))

        CoroutineScope(Dispatchers.Main).launch {
            themeRepository.config.collect {
                MusicWidgetProvider.updateWidget(ctx, _state.value)
            }
        }
    }

    private fun sync(p: Player) {
        val meta = p.mediaMetadata
        val seen = HashMap<String, Int>()   // repeticiones de cada canción, para las claves
        _state.value = PlayerState(
            mediaId = p.currentMediaItem?.mediaId,
            title = meta.title?.toString().orEmpty(),
            artist = meta.artist?.toString().orEmpty(),
            artwork = meta.artworkData,
            queue = List(p.mediaItemCount) { i ->
                val item = p.getMediaItemAt(i)
                val occurrence = seen.merge(item.mediaId, 1, Int::plus) ?: 1
                QueueItem(
                    key = "${item.mediaId}#$occurrence",
                    mediaId = item.mediaId,
                    title = item.mediaMetadata.title?.toString().orEmpty(),
                    artist = item.mediaMetadata.artist?.toString().orEmpty(),
                )
            },
            index = p.currentMediaItemIndex,
            isPlaying = p.isPlaying,
            shuffle = p.shuffleModeEnabled,
            repeatMode = p.repeatMode,
            durationMs = p.duration.coerceAtLeast(0),
        )
        MusicWidgetProvider.updateWidget(ctx, _state.value)
    }

    val positionMs: Long get() = controller?.currentPosition ?: 0L

    // ---- Reproducción ----

    /** Sustituye la cola por [tracks] y empieza a sonar desde [startIndex]. */
    fun play(tracks: List<Track>, startIndex: Int = 0) {
        val c = controller ?: return
        c.setMediaItems(tracks.map { it.toMediaItem() }, startIndex, 0L)
        c.prepare()
        c.play()
    }

    fun toggle() { controller?.run { if (isPlaying) pause() else play() } }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPrevious() }   // <3 s: canción anterior; si no, reinicia
    fun seekTo(ms: Long) { controller?.seekTo(ms) }
    fun toggleShuffle() { controller?.run { shuffleModeEnabled = !shuffleModeEnabled } }

    fun cycleRepeat() {
        controller?.run {
            repeatMode = when (repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    // ---- Cola dinámica ----

    fun playNext(track: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) play(listOf(track))
        else c.addMediaItem(c.currentMediaItemIndex + 1, track.toMediaItem())
    }

    fun addToQueue(track: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) play(listOf(track)) else c.addMediaItem(track.toMediaItem())
    }

    fun move(from: Int, to: Int) {
        val c = controller ?: return
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) {
            c.moveMediaItem(from, to)
        }
    }

    fun removeAt(index: Int) { controller?.removeMediaItem(index) }

    fun jumpTo(index: Int) {
        controller?.run {
            seekToDefaultPosition(index)
            play()
        }
    }
}

fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .build()
    )
    .build()
