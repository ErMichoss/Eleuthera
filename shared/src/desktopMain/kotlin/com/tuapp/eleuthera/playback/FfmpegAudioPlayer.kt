package com.tuapp.eleuthera.playback

import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread

/**
 * Reproduce un archivo de audio de cualquier formato (mp3, webm, opus, flac...):
 * ffmpeg lo decodifica a PCM y Java lo manda a la tarjeta de sonido.
 *
 *   archivo ──ffmpeg──▶ PCM 44,1 kHz estéreo ──hilo lector (volumen)──▶ SourceDataLine ──▶ altavoces
 *
 * - Posición: la cuenta esta clase (lo enviado a la tarjeta menos lo que aún espera en su
 *   búfer), en vez de preguntársela a la tarjeta, que en Windows no es fiable al pausar.
 * - Pausa: se apunta la posición exacta y se cierra todo; reanudar vuelve a lanzar ffmpeg
 *   desde ese punto (-ss). Así nunca salta hacia atrás ni repite trozos.
 * - Cada arranque lleva un número de "generación": si llega el final de un ffmpeg viejo
 *   (porque se cambió de canción mientras acababa), se ignora.
 *
 * Los avisos [onEnded] y [onError] llegan desde el hilo lector, no desde el principal.
 */
class FfmpegAudioPlayer(
    private val ffmpegPath: () -> String?,
    private val onEnded: () -> Unit,
    private val onError: (String) -> Unit,
) {
    private val format = AudioFormat(SAMPLE_RATE.toFloat(), 16, 2, true, false)
    private val frameSize = format.frameSize   // 4 bytes: 2 canales × 16 bits

    private val generation = AtomicInteger()
    private var process: Process? = null
    private var line: SourceDataLine? = null
    private var file: File? = null

    /** Desde qué punto se lanzó el ffmpeg actual (o dónde se quedó en pausa). */
    @Volatile private var startOffsetMs = 0L

    /** Bytes enviados a la tarjeta desde el último arranque. */
    @Volatile private var bytesWritten = 0L

    @Volatile var isPlaying = false
        private set

    /** true si hay una canción cargada (aunque esté en pausa). */
    val isLoaded: Boolean get() = file != null

    /** Volumen de 0 a 1. Se aplica a las muestras antes de enviarlas, así funciona en todos los sistemas. */
    @Volatile var volume: Float = 1f
        set(value) { field = value.coerceIn(0f, 1f) }

    /** Posición actual en ms. */
    val positionMs: Long
        get() {
            val l = line ?: return startOffsetMs
            val pending = (l.bufferSize - l.available()).coerceAtLeast(0)   // aún sin sonar
            val played = (bytesWritten - pending).coerceAtLeast(0) / frameSize
            return startOffsetMs + played * 1000 / SAMPLE_RATE
        }

    /** Carga [file] y, si [autoplay], empieza a sonar desde [startMs]. */
    @Synchronized
    fun load(file: File, startMs: Long = 0, autoplay: Boolean = true) {
        this.file = file
        if (autoplay) start(startMs) else park(startMs)
    }

    @Synchronized
    fun play() {
        if (isPlaying || file == null) return
        start(startOffsetMs)
    }

    @Synchronized
    fun pause() {
        if (!isPlaying) return
        park(positionMs)
    }

    @Synchronized
    fun seekTo(ms: Long) {
        if (file == null) return
        val target = ms.coerceAtLeast(0)
        if (isPlaying) start(target) else park(target)
    }

    /** Para y suelta todo (ffmpeg, la línea de audio y el hilo). */
    @Synchronized
    fun stop() {
        park(0)
        file = null
    }

    /** Deja el reproductor parado en [atMs], sin ffmpeg ni línea de audio abiertos. */
    private fun park(atMs: Long) {
        generation.incrementAndGet()
        release()
        startOffsetMs = atMs
        bytesWritten = 0
        isPlaying = false
    }

    private fun release() {
        process?.destroyForcibly()
        process = null
        line?.let { l -> runCatching { l.stop(); l.flush(); l.close() } }
        line = null
    }

    private fun start(fromMs: Long) {
        val f = file ?: return
        val gen = generation.incrementAndGet()
        release()
        startOffsetMs = fromMs
        bytesWritten = 0

        val ffmpeg = ffmpegPath()
        if (ffmpeg == null) {
            isPlaying = false
            onError("No se encuentra ffmpeg")
            return
        }
        val p = runCatching {
            ProcessBuilder(
                ffmpeg, "-nostdin", "-hide_banner", "-loglevel", "error",
                "-ss", "%.3f".format(Locale.ROOT, fromMs / 1000.0),
                "-i", f.absolutePath,
                "-vn", "-f", "s16le", "-acodec", "pcm_s16le",
                "-ac", "2", "-ar", SAMPLE_RATE.toString(), "-",
            ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        }.getOrElse {
            isPlaying = false
            onError("No se pudo lanzar ffmpeg: ${it.message}")
            return
        }
        val l = runCatching {
            AudioSystem.getSourceDataLine(format).apply {
                // ~0,2 s de margen: suficiente para no cortarse y la pausa responde rápido.
                open(format, SAMPLE_RATE / 5 * frameSize)
                start()
            }
        }.getOrElse {
            p.destroyForcibly()
            isPlaying = false
            onError("No hay salida de audio disponible: ${it.message}")
            return
        }
        process = p
        line = l
        isPlaying = true

        thread(name = "eleuthera-audio", isDaemon = true) { pump(p, l, gen) }
    }

    /** Hilo lector: copia el PCM de ffmpeg a la línea de audio (con el volumen) hasta que se acabe. */
    private fun pump(p: Process, l: SourceDataLine, gen: Int) {
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        var carry = -1   // byte suelto de una muestra partida entre dos lecturas
        try {
            p.inputStream.use { input ->
                while (generation.get() == gen) {
                    var n = input.read(buffer, if (carry >= 0) 1 else 0, buffer.size - 1)
                    if (n < 0) break
                    if (carry >= 0) { buffer[0] = carry.toByte(); n += 1; carry = -1 }
                    // Solo muestras completas (2 bytes); la última mitad se guarda para la próxima.
                    if (n % 2 == 1) { carry = buffer[n - 1].toInt() and 0xFF; n -= 1 }
                    applyVolume(buffer, n)
                    total += n
                    l.write(buffer, 0, n)
                    bytesWritten += n
                }
            }
        } catch (_: Exception) {
            return   // se cerró la línea o el proceso porque se pausó o se cambió de canción
        }
        if (generation.get() != gen) return
        l.drain()   // espera a que suene lo que queda en la línea
        if (generation.get() != gen) return

        val exit = runCatching { p.waitFor() }.getOrDefault(-1)
        if (total == 0L && exit != 0) {
            synchronized(this) { isPlaying = false }
            onError("No se pudo leer el archivo")
        } else {
            onEnded()
        }
    }

    /**
     * Multiplica cada muestra de 16 bits por el volumen. Se eleva al cuadrado porque el oído
     * percibe el volumen de forma logarítmica: así la mitad de la barra suena a "la mitad".
     */
    private fun applyVolume(buf: ByteArray, len: Int) {
        val v = volume
        if (v >= 0.999f) return
        val gain = v * v
        var i = 0
        while (i + 1 < len) {
            val sample = (buf[i].toInt() and 0xFF) or (buf[i + 1].toInt() shl 8)
            val scaled = (sample * gain).toInt()
            buf[i] = scaled.toByte()
            buf[i + 1] = (scaled shr 8).toByte()
            i += 2
        }
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
    }
}