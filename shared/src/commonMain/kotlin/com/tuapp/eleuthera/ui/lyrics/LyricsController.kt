package com.tuapp.eleuthera.ui.lyrics

import com.tuapp.eleuthera.data.lrclib.LrclibHttpException
import com.tuapp.eleuthera.data.lrclib.LrclibLyricsNotFoundException
import com.tuapp.eleuthera.data.lrclib.LyricsRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LyricsTrack(
    val uri: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
)

data class LocalLyricsResult(
    val text: String? = null,
    val embeddedReadError: Throwable? = null,
    val sidecarReadError: Throwable? = null,
)

interface LyricsStorage {
    suspend fun read(track: LyricsTrack): LocalLyricsResult

    suspend fun save(
        track: LyricsTrack,
        lyrics: String,
        isStillCurrent: () -> Boolean,
    ): String
}

enum class LyricsLogLevel { DEBUG, INFO, WARNING, ERROR }

data class LyricsBatchProgress(
    val running: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val found: Int = 0,
    val skipped: Int = 0,
    val notFound: Int = 0,
    val failed: Int = 0,
    val currentTitle: String? = null,
    val message: String? = null,
    val failures: List<String> = emptyList(),
)

data class LyricsLookupResult(
    val text: String?,
    val fromLocal: Boolean,
    val hasSyncedLyrics: Boolean? = null,
    val subtitleMessage: String? = null,
)

/**
 * Shared lyrics workflow: local-first lookup, exact metadata matching, bounded retries,
 * per-folder sequential batches, and diagnostics. Platform file access is delegated to
 * [LyricsStorage].
 */
class LyricsController(
    private val scope: CoroutineScope,
    private val repository: LyricsRepository,
    private val storage: LyricsStorage,
    private val log: (LyricsLogLevel, String, Throwable?) -> Unit = { _, _, _ -> },
) {
    private val _batchProgress = MutableStateFlow<Map<String, LyricsBatchProgress>>(emptyMap())
    val batchProgress: StateFlow<Map<String, LyricsBatchProgress>> = _batchProgress.asStateFlow()

    private var batchJob: Job? = null
    private var activeBatchKey: String? = null

    /**
     * Busca primero en el almacenamiento local y solo consulta LRCLIB si hace falta.
     * La identidad de la pista se vuelve a comprobar antes de guardar para evitar asociar
     * una respuesta tardía a otra canción; los errores temporales usan reintentos acotados.
     */
    suspend fun lookup(
        track: LyricsTrack,
        isStillCurrent: () -> Boolean = { true },
    ): LyricsLookupResult {
        val local = try {
            storage.read(track)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(LyricsLogLevel.WARNING, "No se pudieron consultar letras locales de '${track.title}'.", e)
            LocalLyricsResult(sidecarReadError = e)
        }
        local.embeddedReadError?.let {
            log(LyricsLogLevel.WARNING, "No se pudieron leer letras incrustadas de '${track.title}'.", it)
        }
        local.sidecarReadError?.let {
            log(LyricsLogLevel.WARNING, "No se pudieron leer subtítulos locales de '${track.title}'.", it)
        }
        local.text?.takeIf(String::isNotBlank)?.let {
            log(LyricsLogLevel.DEBUG, "Letras locales encontradas para '${track.title}'.", null)
            return LyricsLookupResult(it, fromLocal = true)
        }
        if (track.title.isBlank() || track.artist.isBlank()) {
            log(LyricsLogLevel.WARNING, "Metadatos insuficientes para buscar '${track.title}'.", null)
            return LyricsLookupResult(null, fromLocal = false)
        }

        for (attempt in 0..MAX_RETRIES) {
            var retryDelayMs = 0L
            try {
                val match = repository.findBestMatch(
                    trackName = track.title,
                    artistName = track.artist,
                    durationMs = track.durationMs,
                    refreshCache = attempt > 0,
                )
                val lyrics = match?.syncedLyrics?.takeIf(String::isNotBlank)
                    ?: match?.plainLyrics?.takeIf(String::isNotBlank)
                if (lyrics == null) {
                    log(
                        LyricsLogLevel.WARNING,
                        "LRCLIB no encontró una coincidencia para '${track.title}' " +
                            "(intento ${attempt + 1}/${MAX_RETRIES + 1}).",
                        null,
                    )
                    if (attempt == MAX_RETRIES) return LyricsLookupResult(null, fromLocal = false)
                    retryDelayMs = RETRY_DELAYS_MS[attempt]
                } else {
                    val lookupContext = currentCoroutineContext()
                    val stillCurrent = { lookupContext.isActive && isStillCurrent() }
                    if (!stillCurrent()) {
                        log(LyricsLogLevel.DEBUG, "La pista '${track.title}' cambió antes de guardar.", null)
                        return LyricsLookupResult(null, fromLocal = false)
                    }
                    val saveMessage = storage.save(track, lyrics, stillCurrent)
                    log(LyricsLogLevel.INFO, "Letra encontrada para '${track.title}'; $saveMessage", null)
                    return LyricsLookupResult(
                        text = lyrics,
                        fromLocal = false,
                        hasSyncedLyrics = !match?.syncedLyrics.isNullOrBlank(),
                        subtitleMessage = saveMessage,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(
                    LyricsLogLevel.WARNING,
                    "Error LRCLIB/procesando '${track.title}' " +
                        "(intento ${attempt + 1}/${MAX_RETRIES + 1}): ${e.message ?: e.javaClass.simpleName}",
                    e,
                )
                if (!shouldRetry(e) || attempt == MAX_RETRIES) throw e
                retryDelayMs = retryDelay(attempt, e)
            }
            delay(retryDelayMs)
        }
        error("Flujo de reintentos LRCLIB finalizado sin resultado.")
    }

    /**
     * Ejecuta un único lote a la vez y en orden, espaciando canciones para no saturar LRCLIB.
     * Pulsar de nuevo la carpeta activa cancela el lote; cada fallo queda registrado sin
     * interrumpir el procesamiento del resto.
     */
    fun toggleBatch(key: String, tracks: List<LyricsTrack>) {
        if (activeBatchKey == key) {
            batchJob?.cancel()
            return
        }
        if (batchJob?.isActive == true) return

        activeBatchKey = key
        batchJob = scope.launch {
            var progress = LyricsBatchProgress(running = true, total = tracks.size)
            _batchProgress.update { it + (key to progress) }
            log(LyricsLogLevel.INFO, "Inicia lote de letras: $key (${tracks.size} canciones).", null)
            try {
                for ((index, track) in tracks.withIndex()) {
                    progress = progress.copy(completed = index, currentTitle = track.title)
                    _batchProgress.update { it + (key to progress) }
                    try {
                        val result = lookup(track)
                        progress = if (result.fromLocal) {
                            progress.copy(completed = index + 1, skipped = progress.skipped + 1)
                        } else if (result.text != null) {
                            progress.copy(completed = index + 1, found = progress.found + 1)
                        } else {
                            progress.copy(completed = index + 1, notFound = progress.notFound + 1)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val reason = e.message ?: e.javaClass.simpleName
                        progress = progress.copy(
                            completed = index + 1,
                            failed = progress.failed + 1,
                            failures = progress.failures + "${track.title}: $reason",
                        )
                        log(LyricsLogLevel.ERROR, "Falló '${track.title}' en el lote $key.", e)
                    }
                    _batchProgress.update { it + (key to progress) }
                    if (index < tracks.lastIndex) delay(BATCH_TRACK_INTERVAL_MS)
                }
                progress = progress.copy(
                    running = false,
                    currentTitle = null,
                    message = "Listo: ${progress.found} guardadas, ${progress.skipped} ya tenían letra, " +
                        "${progress.notFound} sin coincidencia, ${progress.failed} errores.",
                )
                log(LyricsLogLevel.INFO, "Lote terminado: $key; $progress", null)
            } catch (e: CancellationException) {
                progress = progress.copy(
                    running = false,
                    currentTitle = null,
                    message = "Cancelado: ${progress.found} letras guardadas.",
                )
                log(LyricsLogLevel.INFO, "Lote cancelado: $key.", null)
            } catch (e: Exception) {
                progress = progress.copy(
                    running = false,
                    currentTitle = null,
                    message = "No se pudo completar la búsqueda: ${e.message ?: "error desconocido"}",
                )
                log(LyricsLogLevel.ERROR, "Falló el lote $key.", e)
            } finally {
                _batchProgress.update { it + (key to progress) }
                if (activeBatchKey == key) activeBatchKey = null
            }
        }
    }

    /** Solo reintenta fallos recuperables; errores de petición o respuestas 404 son definitivos. */
    private fun shouldRetry(error: Exception): Boolean = when (error) {
        is LrclibLyricsNotFoundException -> false
        is LrclibHttpException -> error.statusCode == 429 || error.statusCode in 500..599
        is IOException -> true
        else -> false
    }

    /** Respeta Retry-After cuando LRCLIB lo proporciona, sin acortar el backoff local. */
    private fun retryDelay(attempt: Int, error: Exception): Long {
        val backoff = RETRY_DELAYS_MS[attempt]
        val serverDelay = (error as? LrclibHttpException)?.retryAfterMs ?: 0L
        return maxOf(backoff, serverDelay)
    }

    private companion object {
        const val MAX_RETRIES = 3
        const val BATCH_TRACK_INTERVAL_MS = 2_000L
        val RETRY_DELAYS_MS = longArrayOf(5_000, 10_000, 20_000)
    }
}
