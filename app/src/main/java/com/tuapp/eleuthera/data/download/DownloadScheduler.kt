package com.tuapp.eleuthera.data.download

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.tuapp.eleuthera.data.db.DownloadDao
import com.tuapp.eleuthera.data.db.PlaylistRequestEntity
import com.tuapp.eleuthera.data.library.MediaLibrary
import com.tuapp.eleuthera.data.playlist.PlaylistRepository
import com.tuapp.eleuthera.data.youtube.YtDlpEngine
import com.tuapp.eleuthera.domain.model.DownloadStatus
import com.tuapp.eleuthera.domain.model.RemoteTrack
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Punto de entrada para encolar y observar descargas. */
@Singleton
class DownloadScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val engine: YtDlpEngine,
    private val dao: DownloadDao,
    private val library: MediaLibrary,
    private val playlists: PlaylistRepository,
) {
    /**
     * Pide la canción, opcionalmente dentro de [playlist] en la posición [index].
     * - Si ya está descargada, solo se añade a la playlist: no se descarga otra vez.
     * - Si está en cola o descargándose, se apunta la playlist y se añadirá al terminar.
     * - Si no, se encola la descarga.
     */
    suspend fun enqueue(track: RemoteTrack, playlist: String? = null, index: Int = 0) {
        // 1. Primero se apunta la petición; el worker la resolverá al terminar.
        if (playlist != null) dao.addRequest(PlaylistRequestEntity(track.id, playlist, index))

        // 2. ¿Ya existe el archivo? Entonces basta con resolver las peticiones ahora.
        dao.trackIdFor(track.id)?.let { trackId ->
            if (library.exists(trackId)) {
                dao.requestsFor(track.id).forEach {
                    playlists.addDownloaded(it.playlistName, trackId, it.position)
                }
                dao.clearRequests(track.id)
                return
            }
            dao.forget(track.id)   // el archivo se borró: hay que volver a descargarlo
        }

        // 3. Descarga. KEEP: si ya está en cola o en curso, no se duplica.
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_URL to track.url,
                    DownloadWorker.KEY_VIDEO_ID to track.id,
                    DownloadWorker.KEY_TITLE to track.title,
                    DownloadWorker.KEY_PLAYLIST to playlist,
                    DownloadWorker.KEY_INDEX to index,
                )
            )
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .addTag(DownloadWorker.TAG)
            .addTag(DownloadWorker.TRACK_TAG_PREFIX + track.id)
            .build()
        // KEEP: si esa canción ya está en cola o descargando, no se duplica.
        workManager.enqueueUniqueWork(uniqueName(track.id), ExistingWorkPolicy.KEEP, request)
    }

    /** Vídeo suelto o playlist completa. Equivale a CONFIG.urls + urls.txt del script. */
    suspend fun enqueueUrl(url: String): Int {
        val resolved = engine.resolve(url)
        enqueueAll(resolved.tracks, resolved.playlistTitle)
        return resolved.tracks.size
    }

    suspend fun enqueueAll(tracks: List<RemoteTrack>, playlist: String?) =
        tracks.forEachIndexed { index, track -> enqueue(track, playlist, index) }

    fun cancel(trackId: String) = workManager.cancelUniqueWork(uniqueName(trackId))

    fun cancelAll() = workManager.cancelAllWorkByTag(DownloadWorker.TAG)

    /** Estado de cada descarga, indexado por id de vídeo. */
    fun observe(): Flow<Map<String, DownloadStatus>> =
        workManager.getWorkInfosByTagFlow(DownloadWorker.TAG).map { infos ->
            buildMap {
                for (info in infos) {
                    val trackId = info.tags
                        .firstOrNull { it.startsWith(DownloadWorker.TRACK_TAG_PREFIX) }
                        ?.removePrefix(DownloadWorker.TRACK_TAG_PREFIX) ?: continue
                    val status = info.toStatus() ?: continue
                    put(trackId, status)
                }
            }
        }

    private fun WorkInfo.toStatus(): DownloadStatus? = when (state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadStatus.Queued
        WorkInfo.State.RUNNING -> {
            val pct = progress.getInt(DownloadWorker.KEY_PROGRESS, -1)
            if (pct < 0) DownloadStatus.Queued else DownloadStatus.Running(pct)
        }
        WorkInfo.State.SUCCEEDED -> DownloadStatus.Done
        WorkInfo.State.FAILED -> DownloadStatus.Failed(
            outputData.getString(DownloadWorker.KEY_ERROR) ?: "Error desconocido"
        )
        WorkInfo.State.CANCELLED -> null
    }

    private fun uniqueName(trackId: String) = "dl_$trackId"
}