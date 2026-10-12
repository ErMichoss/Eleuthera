package com.tuapp.eleuthera.data.importer

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tuapp.eleuthera.data.download.DownloadNotifier
import com.tuapp.eleuthera.data.download.DownloadScheduler
import com.tuapp.eleuthera.data.youtube.YtDlpEngine
import com.tuapp.eleuthera.domain.model.ImportedTrack
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

/**
 * Recorre la lista importada: busca cada canción en YouTube, elige la mejor coincidencia y
 * la encola para descargar dentro de la playlist. Las que no encuentra quedan apuntadas.
 */
@HiltWorker
class ImportWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val engine: YtDlpEngine,
    private val downloads: DownloadScheduler,
    private val notifier: DownloadNotifier,
) : CoroutineWorker(ctx, params) {

    private val playlist get() = inputData.getString(KEY_PLAYLIST).orEmpty()

    override suspend fun getForegroundInfo(): ForegroundInfo =
        notifier.foregroundInfo(id, "Importando $playlist", -1)

    override suspend fun doWork(): Result {
        val listFile = File(inputData.getString(KEY_FILE) ?: return Result.failure())
        val tracks = runCatching {
            Json.decodeFromString<List<ImportedTrack>>(listFile.readText())
        }.getOrElse { return Result.failure() }

        runCatching { setForeground(getForegroundInfo()) }

        val missing = mutableListOf<String>()
        val seenVideos = HashSet<String>()   // misma canción dos veces en la lista: solo una
        tracks.forEachIndexed { index, track ->
            val match = try {
                val query = if (track.artist.isBlank()) track.title else "${track.artist} - ${track.title}"
                TrackMatcher.best(track, engine.search(query, limit = 5))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Búsqueda fallida: ${track.label}", e)
                null
            }
            // Se conserva la posición original aunque falten canciones.
            when {
                match == null -> missing += track.label
                seenVideos.add(match.id) -> downloads.enqueue(match, playlist, index)
            }

            val done = index + 1
            setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to tracks.size, KEY_MISSING to missing.size))
            notifier.update(id, "Importando $playlist ($done/${tracks.size})", done * 100 / tracks.size)
            delay(400)   // ritmo prudente para no disparar el bloqueo de YouTube
        }

        val missingFile = File(applicationContext.filesDir, "imports/missing_$id.txt").apply {
            parentFile?.mkdirs()
            writeText(missing.joinToString("\n"))
        }
        listFile.delete()
        return Result.success(
            workDataOf(
                KEY_DONE to tracks.size,
                KEY_TOTAL to tracks.size,
                KEY_MISSING to missing.size,
                KEY_MISSING_FILE to missingFile.absolutePath,
            )
        )
    }

    companion object {
        const val TAG = "import"
        const val KEY_FILE = "file"
        const val KEY_PLAYLIST = "playlist"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_MISSING = "missing"
        const val KEY_MISSING_FILE = "missing_file"
    }
}