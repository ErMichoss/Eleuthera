package com.tuapp.eleuthera.data.download

import android.content.ContentUris
import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tuapp.eleuthera.data.library.MediaLibrary
import com.tuapp.eleuthera.data.playlist.PlaylistRepository
import com.tuapp.eleuthera.data.youtube.YtDlpEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Limita cuántas descargas corren a la vez (el `threads: 4` del script original). */
@Singleton
class DownloadLimiter @Inject constructor() {
    val semaphore = Semaphore(permits = MAX_PARALLEL)
    companion object { const val MAX_PARALLEL = 4 }
}

/** Descarga una canción y la publica en Música. Un trabajo por canción. */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val engine: YtDlpEngine,
    private val library: MediaLibrary,
    private val notifier: DownloadNotifier,
    private val limiter: DownloadLimiter,
    private val playlists: PlaylistRepository,
) : CoroutineWorker(ctx, params) {

    private val title get() = inputData.getString(KEY_TITLE).orEmpty()

    override suspend fun getForegroundInfo(): ForegroundInfo =
        notifier.foregroundInfo(id, title, -1)

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val playlist = inputData.getString(KEY_PLAYLIST)

        // Primer plano = el sistema no mata la descarga al salir de la app.
        // Puede fallar si el trabajo arranca con la app en segundo plano; se continúa igualmente.
        runCatching { setForeground(getForegroundInfo()) }
            .onFailure { Log.w(TAG, "Sin servicio en primer plano", it) }

        return try {
            limiter.semaphore.withPermit {
                var last = -1
                val file = engine.downloadAudio(url, processId = id.toString()) { pct ->
                    if (pct != last) {              // evita saturar notificaciones
                        last = pct
                        setProgressAsync(workDataOf(KEY_PROGRESS to pct))
                        notifier.update(id, title, pct)
                    }
                }
                val uri = library.publish(file, playlist)
                // Canción de una playlist de YouTube: entra sola en la playlist del mismo nombre,
                // en su posición original aunque las descargas terminen desordenadas.
                if (playlist != null) {
                    playlists.addDownloaded(
                        playlistName = playlist,
                        trackId = ContentUris.parseId(uri),
                        position = inputData.getInt(KEY_INDEX, Int.MAX_VALUE),
                    )
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Fallo al descargar $url", e)
            if (runAttemptCount < MAX_RETRIES) Result.retry()
            else Result.failure(workDataOf(KEY_ERROR to (e.message ?: e.toString()).take(800)))
        }
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_TITLE = "title"
        const val KEY_PLAYLIST = "playlist"
        const val KEY_INDEX = "index"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        const val TAG = "download"
        const val TRACK_TAG_PREFIX = "track:"
        private const val MAX_RETRIES = 2
    }
}
