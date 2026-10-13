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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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
    private val prefs = ctx.getSharedPreferences("imports", Context.MODE_PRIVATE)

    /**
     * Importaciones que el usuario ya cerró. Se guardan en disco porque WorkManager conserva
     * los trabajos terminados varios días, y sin esto volverían a aparecer al reabrir la app.
     */
    private val dismissed = MutableStateFlow(prefs.getStringSet(KEY_DISMISSED, null).orEmpty())

    /** Oculta una importación terminada para siempre y borra su lista de no encontradas. */
    fun dismiss(status: ImportStatus) {
        status.missingFile?.let { File(it).delete() }
        // Se guardan como mucho las 100 últimas: las antiguas ya las habrá olvidado WorkManager.
        val updated = (dismissed.value + status.id.toString()).toList().takeLast(100).toSet()
        prefs.edit().putStringSet(KEY_DISMISSED, updated).apply()
        dismissed.value = updated
    }

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

    /** Importaciones en curso o terminadas que el usuario aún no ha cerrado. */
    fun observe(): Flow<List<ImportStatus>> =
        combine(workManager.getWorkInfosByTagFlow(ImportWorker.TAG), dismissed) { infos, hidden ->
            infos.filter {
                it.state != WorkInfo.State.CANCELLED && it.id.toString() !in hidden
            }.map { info ->
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

    private companion object {
        const val PLAYLIST_TAG = "import_playlist:"
        const val KEY_DISMISSED = "dismissed"
    }
}