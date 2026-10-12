package com.tuapp.eleuthera.data.importer

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.tuapp.eleuthera.domain.model.ImportedTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Estado de una importación para mostrarlo en pantalla. */
data class ImportStatus(
    val id: UUID,
    val playlist: String,
    val done: Int,
    val total: Int,
    val missing: Int,
    val finished: Boolean,
    val missingFile: String?,
)

@Singleton
class ImportScheduler @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val workManager: WorkManager,
) {
    /** Lanza una importación en segundo plano. La lista va a un archivo (puede ser muy larga). */
    fun enqueue(playlist: String, tracks: List<ImportedTrack>) {
        val file = File(ctx.cacheDir, "imports/${UUID.randomUUID()}.json").apply {
            parentFile?.mkdirs()
            writeText(Json.encodeToString(tracks))
        }
        val request = OneTimeWorkRequestBuilder<ImportWorker>()
            .setInputData(
                workDataOf(
                    ImportWorker.KEY_FILE to file.absolutePath,
                    ImportWorker.KEY_PLAYLIST to playlist,
                )
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(ImportWorker.TAG)
            .addTag("$PLAYLIST_TAG$playlist")
            .build()
        workManager.enqueue(request)
    }

    fun cancel(id: UUID) = workManager.cancelWorkById(id)

    /** Importaciones en curso o terminadas recientemente. */
    fun observe(): Flow<List<ImportStatus>> =
        workManager.getWorkInfosByTagFlow(ImportWorker.TAG).map { infos ->
            infos.filter { it.state != WorkInfo.State.CANCELLED }.map { info ->
                val finished = info.state.isFinished
                val data = if (finished) info.outputData else info.progress
                ImportStatus(
                    id = info.id,
                    playlist = info.tags.firstOrNull { it.startsWith(PLAYLIST_TAG) }
                        ?.removePrefix(PLAYLIST_TAG).orEmpty(),
                    done = data.getInt(ImportWorker.KEY_DONE, 0),
                    total = data.getInt(ImportWorker.KEY_TOTAL, 0),
                    missing = data.getInt(ImportWorker.KEY_MISSING, 0),
                    finished = finished,
                    missingFile = data.getString(ImportWorker.KEY_MISSING_FILE),
                )
            }
        }

    private companion object { const val PLAYLIST_TAG = "import_playlist:" }
}