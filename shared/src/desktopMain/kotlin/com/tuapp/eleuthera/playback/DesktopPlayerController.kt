package com.tuapp.eleuthera.playback

import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.platform.FileUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reproductor de escritorio con la misma cola que el de Android (ver [MusicPlayer]).
 * En Android la lista de canciones vive dentro de Media3; aquí vive en [items] y
 * [FfmpegAudioPlayer] solo sabe reproducir la canción actual.
 *
 * Todo se modifica desde el hilo principal ([Dispatchers.Main], el de la interfaz); los
 * avisos del motor de audio se pasan a ese hilo antes de tocar nada.
 */
class DesktopPlayerController(initialVolume: Float = 1f) : MusicPlayer {

    /** Una canción de la lista del reproductor. */
    private data class Entry(val track: Track, val userQueued: Boolean)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** Último error del motor (no hay ffmpeg, archivo ilegible...), para mostrarlo en pantalla. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val engine = FfmpegAudioPlayer(
        ffmpegPath = Ffmpeg::find,
        onEnded = { scope.launch { onTrackEnded() } },
        onError = { msg -> scope.launch { _error.value = msg; sync() } },
    ).apply { volume = initialVolume }

    /** Volumen de 0 a 1 (en escritorio no hay botones de volumen como en el móvil). */
    private val _volume = MutableStateFlow(initialVolume.coerceIn(0f, 1f))
    val volume: StateFlow<Float> = _volume.asStateFlow()

    fun setVolume(value: Float) {
        val v = value.coerceIn(0f, 1f)
        engine.volume = v
        _volume.value = v
    }

    /** [ya escuchadas] · actual · cola del usuario · cola por defecto */
    private val items = mutableListOf<Entry>()
    private var index = -1

    /** Lista desde la que se empezó a reproducir: sirve para regenerar la cola por defecto. */
    private var context: List<Track> = emptyList()
    private var shuffle = false
    private var repeat = RepeatMode.OFF

    /** Duración de la canción actual (de la biblioteca o, si falta, leída con ffmpeg). */
    private var durationMs = 0L
    private var tickerJob: Job? = null

    override val positionMs: Long get() = if (index in items.indices) engine.positionMs else 0L

    private val current: Entry? get() = items.getOrNull(index)

    // ---- Reproducción ----

    override fun play(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        val start = startIndex.coerceIn(tracks.indices)
        context = tracks

        val pendingUser = upcoming().filter { it.userQueued }
        val rest = if (shuffle) tracks.filterIndexed { i, _ -> i != start }.shuffled()
        else tracks.drop(start + 1)

        items.clear()
        items += Entry(tracks[start], userQueued = false)
        items += pendingUser
        items += rest.map { Entry(it, userQueued = false) }
        startAt(0)
    }

    override fun playShuffled(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        shuffle = true
        play(tracks, tracks.indices.random())
    }

    override fun toggle() {
        if (current == null) return
        when {
            engine.isPlaying -> engine.pause()
            engine.isLoaded -> engine.play()
            else -> return startAt(index)   // había terminado la lista: vuelve a empezar la actual
        }
        sync()
    }

    override fun next() {
        ensureNextCycle()
        if (index + 1 < items.size) startAt(index + 1)
    }

    override fun previous() {
        if (engine.positionMs > 3_000 || index <= 0) seekTo(0) else startAt(index - 1)
    }

    override fun seekTo(ms: Long) {
        engine.seekTo(ms.coerceIn(0, durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE))
        sync()
    }

    /** Al cambiar el aleatorio se regenera la cola por defecto; la del usuario no se toca. */
    override fun toggleShuffle() {
        shuffle = !shuffle
        val now = current
        if (context.isNotEmpty() && now != null) {
            removeDefaultUpcoming()
            val rest = if (shuffle) {
                context.filter { it.id != now.track.id }.shuffled()
            } else {
                // Sin aleatorio: desde la canción actual hacia abajo en la playlist.
                val pos = context.indexOfFirst { it.id == now.track.id }
                if (pos >= 0) context.drop(pos + 1) else context
            }
            items += rest.map { Entry(it, userQueued = false) }
        }
        sync()
    }

    override fun cycleRepeat() {
        repeat = when (repeat) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        ensureNextCycle()
        sync()
    }

    // ---- Cola ----

    override fun playNext(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        if (current == null) return play(tracks)
        items.addAll(index + 1, tracks.map { Entry(it, userQueued = true) })
        sync()
    }

    override fun addToQueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        if (current == null) return play(tracks)
        var pos = index + 1
        while (pos < items.size && items[pos].userQueued) pos++
        items.addAll(pos, tracks.map { Entry(it, userQueued = true) })
        sync()
    }

    override fun move(from: Int, to: Int) {
        val valid = (index + 1) until items.size
        if (from in valid && to in valid && from != to) {
            items.add(to, items.removeAt(from))
            sync()
        }
    }

    override fun removeAt(index: Int) {
        if (index !in items.indices || index == this.index) return
        items.removeAt(index)
        if (index < this.index) this.index--
        sync()
    }

    override fun jumpTo(index: Int) {
        if (index in items.indices) startAt(index)
    }

    // ---- Internos ----

    private fun startAt(i: Int) {
        index = i
        val entry = items[i]
        _error.value = null
        durationMs = entry.track.durationMs
        val file = (entry.track.uri as? FileUri)?.file
        if (file == null) {
            _error.value = "No se puede abrir ${entry.track.title}"
            engine.stop()
        } else {
            engine.load(file)
            if (durationMs <= 0) {
                // Archivo sin duración en la biblioteca: se lee de la cabecera, sin bloquear.
                scope.launch {
                    val d = withContext(Dispatchers.IO) { Ffmpeg.durationMs(file) }
                    if (current === entry) { durationMs = d; sync() }
                }
            }
        }
        trimHistory()
        sync()
    }

    /** Fin de canción: repetirla, pasar a la siguiente o parar al final de la lista. */
    private fun onTrackEnded() {
        when {
            repeat == RepeatMode.ONE -> startAt(index)
            else -> {
                ensureNextCycle()
                if (index + 1 < items.size) startAt(index + 1)
                else {
                    engine.stop()
                    sync()
                }
            }
        }
    }

    /** Con "repetir playlist", al llegar a la última canción se añade otra vuelta de la lista. */
    private fun ensureNextCycle() {
        if (repeat != RepeatMode.ALL || context.isEmpty() || items.isEmpty()) return
        if (index < items.size - 1) return
        val cycle = if (shuffle) context.shuffled() else context
        items += cycle.map { Entry(it, userQueued = false) }
    }

    /** Evita que el historial de canciones ya escuchadas crezca sin límite. */
    private fun trimHistory() {
        if (index > MAX_HISTORY + 20) {
            val drop = index - MAX_HISTORY
            items.subList(0, drop).clear()
            index -= drop
        }
    }

    private fun upcoming(): List<Entry> =
        if (index + 1 < items.size) items.subList(index + 1, items.size).toList() else emptyList()

    private fun removeDefaultUpcoming() {
        val keep = upcoming().filter { it.userQueued }
        while (items.size > index + 1) items.removeAt(items.size - 1)
        items += keep
    }

    private fun sync() {
        val now = current
        val seen = HashMap<Long, Int>()   // repeticiones de cada canción, para las claves
        val queue = (index + 1 until items.size).map { i ->
            val e = items[i]
            val occurrence = seen.merge(e.track.id, 1, Int::plus) ?: 1
            QueueItem(
                key = "${e.track.id}#$occurrence",
                index = i,
                mediaId = e.track.id.toString(),
                title = e.track.title,
                artist = e.track.artist,
                userQueued = e.userQueued,
            )
        }
        _state.value = PlayerState(
            mediaId = now?.track?.id?.toString(),
            title = now?.track?.title.orEmpty(),
            artist = now?.track?.artist.orEmpty(),
            artwork = null,   // carátulas: llegan con la biblioteca de escritorio (fase 4)
            currentIndex = index.coerceAtLeast(0),
            userQueue = queue.filter { it.userQueued },
            upNext = queue.filterNot { it.userQueued },
            isPlaying = engine.isPlaying,
            shuffle = shuffle,
            repeat = repeat,
            durationMs = durationMs,
            positionMs = positionMs,
        )
        updateTicker(engine.isPlaying)
    }

    /** Mientras suena, actualiza la posición cada medio segundo. */
    private fun updateTicker(playing: Boolean) {
        tickerJob?.cancel()
        if (!playing) return
        tickerJob = scope.launch {
            while (isActive) {
                delay(500)
                _state.value = _state.value.copy(positionMs = positionMs, isPlaying = engine.isPlaying)
            }
        }
    }

    /** Para el sonido al cerrar la app. */
    fun release() {
        engine.stop()
        scope.launch { tickerJob?.cancel() }
    }

    private companion object { const val MAX_HISTORY = 50 }
}