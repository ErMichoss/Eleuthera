package com.tuapp.eleuthera.data.youtube

import com.tuapp.eleuthera.data.settings.DownloadSettings
import com.tuapp.eleuthera.domain.model.AudioFormat
import com.tuapp.eleuthera.domain.model.RemoteTrack
import com.tuapp.eleuthera.platform.Tools
import com.tuapp.eleuthera.playback.Ffmpeg
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class FlatThumbnail(val url: String)

@Serializable
private data class FlatEntry(
    val id: String,
    val title: String? = null,
    val uploader: String? = null,
    val channel: String? = null,
    val duration: Double? = null,
    val thumbnails: List<FlatThumbnail>? = null,
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
    val thumbnails: List<FlatThumbnail>? = null,
)

/** Resultado de resolver una URL: un vídeo suelto o una playlist expandida. */
data class Resolved(val playlistTitle: String?, val tracks: List<RemoteTrack>)

/**
 * yt-dlp en escritorio: lo mismo que YtDlpEngine en Android, pero lanzando el programa
 * descargado por [Tools] en vez de la librería de Android. Mismas opciones, mismo resultado.
 */
class DesktopYtDlp(private val downloadSettings: DownloadSettings) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String, limit: Int = 20): List<RemoteTrack> =
        resolve("ytsearch$limit:$query").tracks

    /** Acepta búsquedas (ytsearchN:), vídeos y playlists de youtube.com o music.youtube.com. */
    suspend fun resolve(target: String): Resolved {
        val out = exec(listOf("--flat-playlist", "--dump-single-json", "--no-warnings", target))
        val r = json.decodeFromString<FlatResult>(out)
        return if (r.type == "playlist") {
            Resolved(
                playlistTitle = r.title.takeUnless { target.startsWith("ytsearch") },
                tracks = r.entries.map { track(it.id, it.title, it.channel ?: it.uploader, it.duration, it.thumbnails) },
            )
        } else {
            val id = r.id ?: error("yt-dlp no devolvió ningún vídeo")
            Resolved(null, listOf(track(id, r.title, r.channel ?: r.uploader, r.duration, r.thumbnails)))
        }
    }

    /**
     * Descarga el audio en el formato de Ajustes (MP3 o M4A sin recodificar) dentro de [dir]
     * y devuelve el archivo. Si la corrutina se cancela, se mata yt-dlp y se borra [dir].
     */
    suspend fun downloadAudio(url: String, dir: File, onProgress: (Int) -> Unit): File {
        val format = downloadSettings.format.first()
        dir.mkdirs()
        val args = buildList {
            add("--no-playlist")
            add("--newline")   // una línea por actualización de progreso
            add("-x")
            when (format) {
                AudioFormat.MP3 -> addAll(listOf("--audio-format", "mp3", "--audio-quality", "0"))
                // Se pide directamente la pista AAC: ffmpeg solo cambia el contenedor.
                AudioFormat.M4A -> addAll(listOf("-f", "bestaudio[ext=m4a]/bestaudio", "--audio-format", "m4a"))
            }
            addAll(listOf("--embed-metadata", "--embed-thumbnail", "--convert-thumbnails", "jpg"))
            addAll(listOf("-o", File(dir, "%(title)s.%(ext)s").absolutePath))
            add(url)
        }
        try {
            exec(args) { line ->
                PROGRESS.find(line)?.let { onProgress(it.groupValues[1].toDouble().toInt().coerceIn(0, 100)) }
            }
        } catch (e: Throwable) {
            dir.deleteRecursively()
            throw e
        }
        return dir.listFiles()?.firstOrNull { it.extension.equals(format.extension, ignoreCase = true) }
            ?: error("yt-dlp terminó sin generar el archivo de audio")
    }

    /**
     * Lanza yt-dlp con [args]. Si se pasa [onLine], la salida se va entregando línea a línea
     * (progreso); si no, se devuelve entera (JSON). Lanza excepción con el error de yt-dlp.
     */
    private suspend fun exec(args: List<String>, onLine: ((String) -> Unit)? = null): String = coroutineScope {
        val yt = Tools.ytDlp() ?: error("yt-dlp no está instalado todavía")
        val command = buildList {
            add(yt)
            addAll(listOf("--encoding", "utf-8"))
            // ffmpeg y ffprobe para convertir y poner la carátula.
            Ffmpeg.find()?.let { File(it) }?.takeIf { it.isAbsolute }?.let {
                addAll(listOf("--ffmpeg-location", it.parent))
            }
            // Deno descargado por la app (solo con nuestro yt-dlp, que entiende la opción).
            val deno = Tools.deno()
            if (Tools.isBundled(yt) && deno != null && File(deno).isAbsolute) {
                addAll(listOf("--js-runtimes", "deno:$deno"))
            }
            addAll(args)
        }
        val process = ProcessBuilder(command)
            .apply { environment()["PYTHONIOENCODING"] = "utf-8" }
            .start()
        try {
            val stderr = StringBuilder()
            val errJob = launch(Dispatchers.IO) {
                process.errorStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                    if (stderr.length < 16_000) stderr.appendLine(line)
                }
            }
            val stdout = async(Dispatchers.IO) {
                val reader = process.inputStream.bufferedReader(Charsets.UTF_8)
                if (onLine == null) reader.readText()
                else {
                    reader.forEachLine(onLine)
                    ""
                }
            }
            val code = process.onExit().await().exitValue()
            errJob.join()
            val out = stdout.await()
            if (code != 0) error(errorMessage(stderr.toString(), code))
            out
        } finally {
            // Cancelado (o error): se mata yt-dlp y el ffmpeg que haya lanzado.
            if (process.isAlive) {
                process.descendants().forEach { it.destroyForcibly() }
                process.destroyForcibly()
            }
        }
    }

    private fun errorMessage(stderr: String, code: Int): String {
        val lines = stderr.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return (lines.lastOrNull { it.startsWith("ERROR") } ?: lines.lastOrNull() ?: "yt-dlp falló (código $code)")
            .removePrefix("ERROR:").trim().take(800)
    }

    private fun track(id: String, title: String?, author: String?, duration: Double?, thumbnails: List<FlatThumbnail>?) =
        RemoteTrack(
            id = id,
            title = title.orEmpty(),
            author = author.orEmpty(),
            durationSec = duration?.toLong() ?: 0,
            url = "https://www.youtube.com/watch?v=$id",
            thumbnailUrl = thumbnails?.firstOrNull()?.url.orEmpty(),
        )

    private companion object {
        val PROGRESS = Regex("""\[download]\s+(\d+(?:\.\d+)?)%""")
    }
}