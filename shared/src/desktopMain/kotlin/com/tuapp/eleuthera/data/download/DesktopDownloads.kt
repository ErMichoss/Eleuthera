package com.tuapp.eleuthera.data.download

import com.tuapp.eleuthera.data.db.DownloadDao
import com.tuapp.eleuthera.data.db.VideoTrackEntity
import com.tuapp.eleuthera.data.library.DesktopLibrary
import com.tuapp.eleuthera.data.youtube.DesktopYtDlp
import com.tuapp.eleuthera.domain.model.DownloadStatus
import com.tuapp.eleuthera.domain.model.RemoteTrack
import com.tuapp.eleuthera.platform.DesktopPaths
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Cola de descargas de escritorio. Hace lo de DownloadScheduler + DownloadWorker en
 * Android: como mucho [MAX_PARALLEL] a la vez, reintentos, sin duplicados, y si la canción
 * ya está descargada no se vuelve a bajar.
 *
 * Las canciones de una playlist van a su subcarpeta de Música/Eleuthera, igual que en
 * Android. Añadirlas a la playlist de la app llegará con las pantallas (fase 5).
 *
 * La cola vive en memoria: si se cierra la app, lo pendiente se pierde.
 */
class DesktopDownloads(
    private val engine: DesktopYtDlp,
    private val dao: DownloadDao,
    private val library: DesktopLibrary,
) {
    data class Item(val track: RemoteTrack, val playlist: String?, val status: DownloadStatus)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val semaphore = Semaphore(MAX_PARALLEL)
    private val lock = Any()
    private val jobs = HashMap<String, Job>()

    /** Todas las descargas de esta sesión, en el orden en que se pidieron. */
    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items.asStateFlow()

    /** Pide la canción. Si ya está en cola o descargándose, no hace nada. */
    fun enqueue(track: RemoteTrack, playlist: String? = null) = synchronized(lock) {
        val existing = _items.value.firstOrNull { it.track.id == track.id }
        if (existing != null && (existing.status is DownloadStatus.Queued || existing.status is DownloadStatus.Running)) {
            return@synchronized
        }
        val item = Item(track, playlist, DownloadStatus.Queued)
        _items.update { list ->
            if (existing != null) list.map { if (it.track.id == track.id) item else it } else list + item
        }
        jobs[track.id] = scope.launch { run(track, playlist) }
    }

    /** Vídeo suelto o playlist completa. Devuelve cuántas canciones se han pedido. */
    suspend fun enqueueUrl(url: String): Int {
        val resolved = engine.resolve(url)
        resolved.tracks.forEach { enqueue(it, resolved.playlistTitle) }
        return resolved.tracks.size
    }

    fun cancel(videoId: String) {
        synchronized(lock) { jobs.remove(videoId) }?.cancel()
        _items.update { list -> list.filterNot { it.track.id == videoId } }
    }

    fun cancelAll() {
        synchronized(lock) { jobs.values.toList().also { jobs.clear() } }.forEach { it.cancel() }
        _items.update { list ->
            list.filterNot { it.status is DownloadStatus.Queued || it.status is DownloadStatus.Running }
        }
    }

    fun retry(videoId: String) {
        _items.value.firstOrNull { it.track.id == videoId }?.let { enqueue(it.track, it.playlist) }
    }

    /** Quita de la lista las terminadas y las fallidas. */
    fun clearFinished() = _items.update { list ->
        list.filter { it.status is DownloadStatus.Queued || it.status is DownloadStatus.Running }
    }

    private suspend fun run(track: RemoteTrack, playlist: String?) {
        try {
            // ¿Ya la tenemos? Entonces no se descarga otra vez.
            dao.trackIdFor(track.id)?.let { trackId ->
                if (library.tracks.value?.any { it.id == trackId } == true) {
                    setStatus(track.id, DownloadStatus.Done)
                    return
                }
                dao.forget(track.id)   // el archivo se borró: hay que volver a descargarlo
            }
            semaphore.withPermit {
                var attempt = 0
                while (true) {
                    try {
                        setStatus(track.id, DownloadStatus.Running(0))
                        val dir = File(DesktopPaths.dataDir, "cache/dl/${UUID.randomUUID()}")
                        var last = -1
                        val file = engine.downloadAudio(track.url, dir) { pct ->
                            if (pct != last) {
                                last = pct
                                setStatus(track.id, DownloadStatus.Running(pct))
                            }
                        }
                        val dest = publish(file, playlist)
                        dao.setTrack(VideoTrackEntity(track.id, DesktopLibrary.idFor(dest.absolutePath)))
                        library.refresh()
                        setStatus(track.id, DownloadStatus.Done)
                        return
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (++attempt > MAX_RETRIES) {
                            setStatus(track.id, DownloadStatus.Failed(e.message ?: e.toString()))
                            return
                        }
                        delay(2_000L * attempt)
                    }
                }
            }
        } finally {
            synchronized(lock) { jobs.remove(track.id) }
        }
    }

    private fun setStatus(videoId: String, status: DownloadStatus) = _items.update { list ->
        list.map { if (it.track.id == videoId) it.copy(status = status) else it }
    }

    /**
     * Mueve el archivo descargado a Música/Eleuthera (o a su subcarpeta de playlist), sin
     * pisar uno que ya exista con el mismo nombre. Devuelve dónde ha quedado.
     */
    private fun publish(file: File, playlist: String?): File {
        val folder = playlist?.replace(INVALID_CHARS, "_")?.trim()?.trimEnd('.')?.ifEmpty { null }
        val dir = (folder?.let { File(DesktopPaths.musicDir, it) } ?: DesktopPaths.musicDir).apply { mkdirs() }
        var dest = File(dir, file.name)
        var n = 2
        while (dest.exists()) {
            dest = File(dir, "${file.nameWithoutExtension} ($n).${file.extension}")
            n++
        }
        try {
            Files.move(file.toPath(), dest.toPath())
        } catch (e: IOException) {
            file.copyTo(dest)   // otro disco: copiar
        }
        file.parentFile?.deleteRecursively()
        return dest.absoluteFile
    }

    companion object {
        const val MAX_PARALLEL = 4
        private const val MAX_RETRIES = 2
        private val INVALID_CHARS = Regex("""[\\/:*?"<>|]""")
    }
}