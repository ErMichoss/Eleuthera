package com.tuapp.eleuthera.ui.library

import android.content.IntentSender
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.eleuthera.data.library.MediaLibrary
import com.tuapp.eleuthera.data.lrclib.LrclibLyrics
import com.tuapp.eleuthera.data.lrclib.LrclibService
import com.tuapp.eleuthera.data.metadata.EmbeddedLyricsReader
import com.tuapp.eleuthera.data.metadata.LyricsSubtitleWriter
import com.tuapp.eleuthera.data.metadata.bestLyricsMatch
import com.tuapp.eleuthera.domain.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Biblioteca en Android: le pasa a [LibraryController] (común) las canciones de MediaLibrary.
 * La búsqueda, las pestañas y los grupos los lleva el controlador.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: MediaLibrary,
    private val lyricsReader: EmbeddedLyricsReader,
    private val subtitleWriter: LyricsSubtitleWriter,
    private val lrclib: LrclibService,
) : ViewModel() {

    val controller = LibraryController()
    val state = controller.state

    private var job: Job? = null
    private var lyricsJob: Job? = null
    private var activeLyricsFolder: String? = null
    private val _folderLyricsProgress =
        MutableStateFlow<Map<String, FolderLyricsProgress>>(emptyMap())
    val folderLyricsProgress: StateFlow<Map<String, FolderLyricsProgress>> =
        _folderLyricsProgress.asStateFlow()

    fun start() {
        library.refreshFolders()
        if (job != null) return
        job = viewModelScope.launch {
            library.observe().collect(controller::setTracks)
        }
    }

    fun toggleFolderLyrics(group: LibraryGroup) {
        if (activeLyricsFolder == group.name) {
            lyricsJob?.cancel()
            return
        }
        if (lyricsJob?.isActive == true) return

        activeLyricsFolder = group.name
        lyricsJob = viewModelScope.launch {
            var progress = FolderLyricsProgress(running = true)
            _folderLyricsProgress.update { it + (group.name to progress) }
            try {
                val tracks = group.tracks.distinctBy { it.uri.toString() }
                Log.i(TAG, "Inicia búsqueda de letras: carpeta=${group.name}, canciones=${tracks.size}")
                progress = progress.copy(total = tracks.size)
                _folderLyricsProgress.update { it + (group.name to progress) }

                for ((index, track) in tracks.withIndex()) {
                    progress = progress.copy(completed = index, currentTitle = track.title)
                    _folderLyricsProgress.update { it + (group.name to progress) }
                    try {
                        when (processTrackWithRetries(track)) {
                            TrackLyricsResult.ALREADY_AVAILABLE -> progress = progress.copy(
                                completed = index + 1,
                                skipped = progress.skipped + 1,
                            )
                            TrackLyricsResult.NOT_FOUND -> progress = progress.copy(
                                completed = index + 1,
                                notFound = progress.notFound + 1,
                            )
                            TrackLyricsResult.SAVED -> progress = progress.copy(
                                completed = index + 1,
                                found = progress.found + 1,
                            )
                        }
                        Log.i(TAG, "Resultado de letras para '${track.title}': ${progress.completed}/${tracks.size}")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Falló el procesamiento de '${track.title}' (${track.uri})", e)
                        progress = progress.copy(
                            completed = index + 1,
                            failed = progress.failed + 1,
                            failures = progress.failures +
                                "${track.title}: ${e.message ?: "error desconocido"}",
                        )
                    }
                    _folderLyricsProgress.update { it + (group.name to progress) }
                    if (index < tracks.lastIndex) delay(TRACK_REQUEST_INTERVAL_MS)
                }
                progress = progress.copy(
                    running = false,
                    currentTitle = null,
                    message = "Listo: ${progress.found} guardadas, ${progress.skipped} ya tenían letra, " +
                        "${progress.notFound} sin coincidencia, ${progress.failed} errores.",
                )
                Log.i(
                    TAG,
                    "Búsqueda terminada: carpeta=${group.name}, guardadas=${progress.found}, " +
                        "existentes=${progress.skipped}, sinCoincidencia=${progress.notFound}, fallidas=${progress.failed}",
                )
            } catch (e: CancellationException) {
                progress = progress.copy(
                    running = false,
                    currentTitle = null,
                    message = "Cancelado: ${progress.found} letras guardadas.",
                )
            } catch (e: Exception) {
                progress = progress.copy(
                    running = false,
                    currentTitle = null,
                    message = "No se pudo buscar letras: ${e.message ?: "error desconocido"}",
                )
            } finally {
                _folderLyricsProgress.update { it + (group.name to progress) }
                if (activeLyricsFolder == group.name) activeLyricsFolder = null
            }
        }
    }

    private suspend fun processTrackWithRetries(track: Track): TrackLyricsResult {
        val uri = android.net.Uri.parse(track.uri.toString())
        var localLyricsChecked = false
        var lastFailure: Exception? = null
        for (attempt in 0..MAX_TRACK_RETRIES) {
            try {
                if (!localLyricsChecked) {
                    localLyricsChecked = true
                    val hasEmbeddedLyrics = try {
                        !lyricsReader.read(uri).isNullOrBlank()
                    } catch (_: Exception) {
                        false
                    }
                    val hasSidecarLyrics = try {
                        !subtitleWriter.readLocal(uri).isNullOrBlank()
                    } catch (_: Exception) {
                        false
                    }
                    val hasLocalLyrics = hasEmbeddedLyrics || hasSidecarLyrics
                    Log.d(
                        TAG,
                        "Letras locales para '${track.title}': incrustadas=$hasEmbeddedLyrics, " +
                            "archivoLrcSrt=$hasSidecarLyrics",
                    )
                    if (hasLocalLyrics) return TrackLyricsResult.ALREADY_AVAILABLE
                }

                val match: LrclibLyrics? = lrclib
                    .searchLyricsByMetadata(
                        trackName = track.title,
                        artistName = track.artist,
                        refreshCache = attempt > 0,
                    )
                    .bestLyricsMatch(track.title, track.artist, track.durationMs)
                val lyrics = match?.syncedLyrics?.takeIf(String::isNotBlank)
                    ?: match?.plainLyrics?.takeIf(String::isNotBlank)
                if (lyrics != null) {
                    val context = currentCoroutineContext()
                    val savedAt = subtitleWriter.save(uri, lyrics) { context.isActive }
                    Log.i(
                        TAG,
                        "LRCLIB encontró letra para '${track.title}' en intento ${attempt + 1}; $savedAt",
                    )
                    return TrackLyricsResult.SAVED
                }
                Log.w(TAG, "LRCLIB sin coincidencia para '${track.title}', intento ${attempt + 1}")
                if (attempt == MAX_TRACK_RETRIES) return TrackLyricsResult.NOT_FOUND
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastFailure = e
                Log.w(
                    TAG,
                    "Error en '${track.title}', intento ${attempt + 1}/${MAX_TRACK_RETRIES + 1}: " +
                        (e.message ?: e.javaClass.simpleName),
                    e,
                )
                if (attempt == MAX_TRACK_RETRIES) throw e
            }
            delay(RETRY_DELAYS_MS[attempt])
        }
        throw lastFailure ?: IOException("No se pudo procesar la canción.")
    }

    private enum class TrackLyricsResult { ALREADY_AVAILABLE, NOT_FOUND, SAVED }

    /** Borra la canción del dispositivo. Devuelve un IntentSender si Android pide confirmación. */
    suspend fun delete(track: Track): IntentSender? =
        runCatching { library.delete(track) }.getOrNull()

    private companion object {
        const val TAG = "LyricsBatch"
        const val MAX_TRACK_RETRIES = 3
        const val TRACK_REQUEST_INTERVAL_MS = 2_000L
        val RETRY_DELAYS_MS = longArrayOf(5_000, 10_000, 20_000)
    }
}
