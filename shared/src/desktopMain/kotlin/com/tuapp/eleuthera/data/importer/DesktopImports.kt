package com.tuapp.eleuthera.data.importer

import com.tuapp.eleuthera.data.download.DesktopDownloads
import com.tuapp.eleuthera.data.youtube.DesktopYtDlp
import com.tuapp.eleuthera.domain.model.ImportedTrack
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Importaciones de listas en escritorio (lo que en Android hacen ImportScheduler + ImportWorker):
 * cada canción se busca en YouTube, se elige la mejor coincidencia con [TrackMatcher] (común)
 * y se manda a la cola de descargas dentro de su playlist. Las que no aparecen se apuntan.
 *
 * Viven en memoria: si se cierra la app a medias, lo que ya estaba en cola de descarga
 * también se pierde (igual que la cola de descargas de escritorio).
 */
class DesktopImports(
    private val engine: DesktopYtDlp,
    private val downloads: DesktopDownloads,
) {
    data class ImportJob(
        val id: String,
        val playlist: String,
        val done: Int,
        val total: Int,
        val missing: List<String>,
        val finished: Boolean,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = HashMap<String, kotlinx.coroutines.Job>()

    private val _jobs = MutableStateFlow<List<ImportJob>>(emptyList())
    val jobs: StateFlow<List<ImportJob>> = _jobs.asStateFlow()

    /** Lee el archivo y agrupa por playlist ([fallbackName] si no trae nombres). */
    fun parse(text: String, fallbackName: String): Map<String, List<ImportedTrack>> {
        val tracks = ImportFileParser.parse(text)
        require(tracks.isNotEmpty()) { "No se encontraron canciones en el archivo" }
        return tracks.groupBy { it.playlist ?: fallbackName }
    }

    fun enqueue(playlist: String, tracks: List<ImportedTrack>) {
        val id = UUID.randomUUID().toString()
        _jobs.update { it + ImportJob(id, playlist, 0, tracks.size, emptyList(), finished = false) }
        val job = scope.launch { run(id, playlist, tracks) }
        synchronized(running) { running[id] = job }
        job.invokeOnCompletion { synchronized(running) { running.remove(id) } }
    }

    fun cancel(id: String) {
        synchronized(running) { running.remove(id) }?.cancel()
        dismiss(id)
    }

    fun dismiss(id: String) = _jobs.update { list -> list.filterNot { it.id == id } }

    private suspend fun run(id: String, playlist: String, tracks: List<ImportedTrack>) {
        val missing = mutableListOf<String>()
        val seenVideos = HashSet<String>()   // misma canción dos veces en la lista: solo una
        tracks.forEachIndexed { index, track ->
            val match = try {
                val query = if (track.artist.isBlank()) track.title else "${track.artist} - ${track.title}"
                TrackMatcher.best(track, engine.search(query, limit = 5))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            // Se conserva la posición original aunque falten canciones.
            when {
                match == null -> missing += track.label
                seenVideos.add(match.id) -> downloads.enqueue(match, playlist, index)
            }
            val done = index + 1
            update(id) { it.copy(done = done, missing = missing.toList()) }
            delay(400)   // ritmo prudente para no disparar el bloqueo de YouTube
        }
        update(id) { it.copy(finished = true) }
    }

    private fun update(id: String, change: (ImportJob) -> ImportJob) =
        _jobs.update { list -> list.map { if (it.id == id) change(it) else it } }
}
