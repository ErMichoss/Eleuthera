package com.tuapp.eleuthera.data.youtube

import android.content.Context
import android.util.Log
import com.tuapp.eleuthera.data.settings.CookieStore
import com.tuapp.eleuthera.data.settings.DownloadSettings
import com.tuapp.eleuthera.domain.model.AudioFormat
import com.tuapp.eleuthera.domain.model.RemoteTrack
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class FlatEntry(
    val id: String,
    val title: String? = null,
    val uploader: String? = null,
    val channel: String? = null,
    val duration: Double? = null,
)

@Serializable
private data class FlatResult(
    val id: String? = null,
    val title: String? = null,
    val uploader: String? = null,
    val channel: String? = null,
    val duration: Double? = null,
    @SerialName("_type") val type: String? = null,
    val entries: List<FlatEntry> = emptyList(),
)

/** Resultado de resolver una URL: un vídeo suelto o una playlist expandida. */
data class Resolved(val playlistTitle: String?, val tracks: List<RemoteTrack>)

/**
 * Única puerta de entrada a yt-dlp. Equivale a la línea `yt-dlp.exe ...` del script original.
 */
@Singleton
class YtDlpEngine @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val cookieStore: CookieStore,
    private val downloadSettings: DownloadSettings,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val initMutex = Mutex()
    @Volatile private var ready = false

    /**
     * Inicializa yt-dlp y FFmpeg y actualiza yt-dlp, una sola vez por arranque.
     * La actualización reescribe el binario en disco, así que va bajo el mismo candado:
     * ninguna búsqueda o descarga puede ejecutarse mientras el archivo está a medio escribir.
     */
    suspend fun ensureReady() {
        if (ready) return
        initMutex.withLock {
            if (ready) return
            withContext(Dispatchers.IO) {
                YoutubeDL.getInstance().init(ctx)
                FFmpeg.getInstance().init(ctx)
                runCatching {
                    YoutubeDL.getInstance().updateYoutubeDL(ctx, YoutubeDL.UpdateChannel.STABLE)
                }.onFailure { Log.w(TAG, "No se pudo actualizar yt-dlp", it) }
            }
            ready = true
        }
    }

    suspend fun search(query: String, limit: Int = 20): List<RemoteTrack> =
        resolve("ytsearch$limit:$query").tracks

    /** Acepta búsquedas (ytsearchN:), vídeos y playlists de youtube.com o music.youtube.com. */
    suspend fun resolve(target: String): Resolved {
        ensureReady()
        val tmp = File(ctx.cacheDir, "resolve/${UUID.randomUUID()}").apply { mkdirs() }
        val out = try {
            val request = YoutubeDLRequest(target).apply {
                addOption("--flat-playlist")
                addOption("--dump-single-json")
                addOption("--no-warnings")
                // Si el usuario cargó sus cookies en Ajustes, se usan en todas las peticiones.
                cookieStore.snapshotInto(tmp)?.let { addOption("--cookies", it.absolutePath) }
            }
            runInterruptible(Dispatchers.IO) { YoutubeDL.getInstance().execute(request).out }
        } finally {
            tmp.deleteRecursively()
        }
        val r = json.decodeFromString<FlatResult>(out)
        return if (r.type == "playlist") {
            val isSearch = target.startsWith("ytsearch")
            Resolved(
                playlistTitle = r.title.takeUnless { isSearch },
                tracks = r.entries.map {
                    track(it.id, it.title, it.channel ?: it.uploader, it.duration)
                },
            )
        } else {
            val id = r.id ?: error("yt-dlp no devolvió ningún vídeo")
            Resolved(null, listOf(track(id, r.title, r.channel ?: r.uploader, r.duration)))
        }
    }

    /**
     * Descarga el audio en el formato elegido en Ajustes (MP3 o M4A sin recodificar)
     * a un directorio temporal y devuelve el archivo final.
     * [processId] identifica el proceso para poder cancelarlo.
     */
    suspend fun downloadAudio(
        url: String,
        processId: String,
        onProgress: (Int) -> Unit,
    ): File {
        ensureReady()
        val format = downloadSettings.format.first()
        val dir = File(ctx.cacheDir, "dl/$processId").apply { mkdirs() }
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("-x")
            when (format) {
                AudioFormat.MP3 -> {
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "0")
                }
                AudioFormat.M4A -> {
                    // Se pide directamente la pista AAC: FFmpeg solo cambia el contenedor.
                    addOption("-f", "bestaudio[ext=m4a]/bestaudio")
                    addOption("--audio-format", "m4a")
                }
            }
            addOption("--embed-metadata")
            addOption("--embed-thumbnail")
            addOption("--convert-thumbnails", "jpg")
            addOption("-o", "${dir.absolutePath}/%(title)s.%(ext)s")
            cookieStore.snapshotInto(dir)?.let { addOption("--cookies", it.absolutePath) }
        }
        try {
            // execute() es bloqueante; runInterruptible permite cancelarlo desde la corrutina.
            runInterruptible(Dispatchers.IO) {
                YoutubeDL.getInstance().execute(request, processId) { progress, _, _ ->
                    onProgress(progress.toInt().coerceIn(0, 100))
                }
            }
        } catch (e: CancellationException) {
            YoutubeDL.getInstance().destroyProcessById(processId)
            dir.deleteRecursively()
            throw e
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
        return dir.listFiles()?.firstOrNull { it.extension == format.extension }
            ?: error("yt-dlp terminó sin generar el archivo de audio")
    }

    private fun track(id: String, title: String?, author: String?, duration: Double?) =
        RemoteTrack(
            id = id,
            title = title.orEmpty(),
            author = author.orEmpty(),
            durationSec = duration?.toLong() ?: 0,
            url = "https://www.youtube.com/watch?v=$id",
        )

    private companion object { const val TAG = "YtDlpEngine" }
}
