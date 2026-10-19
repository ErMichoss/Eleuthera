package com.tuapp.eleuthera.data.library

import com.tuapp.eleuthera.data.db.FolderTrackDao
import com.tuapp.eleuthera.data.db.FolderTrackEntity
import com.tuapp.eleuthera.data.settings.DesktopLibrarySettings
import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.platform.DesktopPaths
import com.tuapp.eleuthera.platform.FileUri
import com.tuapp.eleuthera.playback.Ffmpeg
import java.io.File
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * Biblioteca de escritorio: las canciones de las carpetas de [DesktopLibrarySettings].
 * Funciona como FolderLibrary en Android:
 *
 * 1. Listar las carpetas es barato: se hace al abrir, al cambiar las carpetas, al pedirlo
 *    ([refresh]) y cada [RESCAN_MS] por si han aparecido archivos nuevos.
 * 2. Los archivos nuevos o modificados salen al momento con lo que dice su nombre.
 * 3. Sus etiquetas se leen con ffmpeg en segundo plano y se guardan en la base de datos;
 *    mientras el archivo no cambie no se vuelve a abrir.
 *
 * Usa la misma tabla que Android (folder_tracks): documentId es la ruta del archivo.
 */
class DesktopLibrary(
    private val settings: DesktopLibrarySettings,
    private val dao: FolderTrackDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** null hasta tener la primera lista (para no mostrar "biblioteca vacía" en falso). */
    private val _tracks = MutableStateFlow<List<Track>?>(null)
    val tracks: StateFlow<List<Track>?> = _tracks.asStateFlow()

    /** Canciones a las que aún les faltan las etiquetas (para mostrar el progreso). */
    private val _pendingTags = MutableStateFlow(0)
    val pendingTags: StateFlow<Int> = _pendingTags.asStateFlow()

    private val refreshRequests =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val tagDispatcher = Dispatchers.IO.limitedParallelism(TAG_THREADS)

    init {
        scope.launch {
            // Lo que ya conocíamos sale enseguida; la sincronización lo corrige después.
            _tracks.value = dao.all().map { it.toTrack() }
            val ticks = flow {
                while (true) {
                    emit(Unit)
                    delay(RESCAN_MS)
                }
            }
            combine(settings.folders, merge(ticks, refreshRequests)) { folders, _ -> folders }
                .collectLatest { sync(it) }
        }
    }

    /** Vuelve a mirar las carpetas ahora (p. ej. al terminar una descarga). */
    fun refresh() {
        refreshRequests.tryEmit(Unit)
    }

    private suspend fun sync(roots: List<File>) {
        val available = roots.filter { it.isDirectory }
        val files = available.flatMap { root -> listAudio(root) }.distinctBy { it.path }

        val cache = dao.all().associateBy { it.documentId }

        // Lo que ya no está. Si una carpeta no se puede leer (disco externo desconectado) sus
        // canciones se conservan; las de carpetas quitadas en Ajustes sí se borran.
        val present = files.mapTo(HashSet()) { it.path }
        val gone = cache.keys.filter { path ->
            path !in present && roots.filter { path.isInside(it) }.let { owners ->
                owners.isEmpty() || owners.any { it in available }
            }
        }
        gone.chunked(500).forEach { dao.delete(it) }

        val entries = files.map { f ->
            val cached = cache[f.path]
            if (cached != null && cached.lastModified == f.lastModified && cached.size == f.size) {
                if (cached.folder == f.folder) cached else cached.copy(folder = f.folder)
            } else {
                fromFileName(f)
            }
        }
        dao.upsert(entries.filter { cache[it.documentId] != it })

        val current = LinkedHashMap<String, FolderTrackEntity>().apply {
            entries.forEach { put(it.documentId, it) }
        }
        publish(current.values)

        // Etiquetas en segundo plano, varias a la vez, actualizando la lista según avanzan.
        val untagged = entries.filter { !it.tagged }
        _pendingTags.value = untagged.size
        if (untagged.isEmpty() || Ffmpeg.find() == null) return
        var left = untagged.size
        untagged.chunked(TAG_BATCH).forEach { batch ->
            coroutineContext.ensureActive()
            val tagged = coroutineScope {
                batch.map { async(tagDispatcher) { readTags(it) } }.awaitAll()
            }
            dao.upsert(tagged)
            tagged.forEach { current[it.documentId] = it }
            publish(current.values)
            left -= batch.size
            _pendingTags.value = left
        }
    }

    private fun publish(entries: Collection<FolderTrackEntity>) {
        _tracks.value = entries.map { it.toTrack() }
    }

    /** Archivos de audio de [root] y sus subcarpetas (sin los ocultos). */
    private fun listAudio(root: File): List<FileEntry> =
        root.walkTopDown()
            .maxDepth(MAX_DEPTH)
            .onEnter { it == root || !it.name.startsWith(".") }
            .filter { it.isFile && !it.name.startsWith(".") && it.extension.lowercase() in AUDIO_EXTENSIONS }
            .map { f ->
                FileEntry(
                    path = f.absolutePath,
                    name = f.name,
                    folder = folderLabel(f, root),
                    lastModified = f.lastModified(),
                    size = f.length(),
                )
            }
            .toList()

    private fun readTags(e: FolderTrackEntity): FolderTrackEntity {
        // Si ffmpeg no puede con el archivo se queda con el nombre y no se reintenta.
        val p = Ffmpeg.probe(File(e.documentId)) ?: return e.copy(tagged = true)
        return e.copy(
            title = p.tag("title") ?: e.title,
            artist = p.tag("artist", "album_artist", "albumartist") ?: e.artist,
            album = p.tag("album") ?: e.album,
            durationMs = p.durationMs.takeIf { it > 0 } ?: e.durationMs,
            tagged = true,
        )
    }

    /** "Artista - Título [id].webm" -> artista y título, sin abrir el archivo. */
    private fun fromFileName(f: FileEntry): FolderTrackEntity {
        val base = f.name.substringBeforeLast('.')
            .replace(YT_ID_SUFFIX, "")   // sufijo " [dQw4w9WgXcQ]" que añade yt-dlp
            .trim()
        val parts = base.split(" - ", limit = 2)
        val (artist, title) =
            if (parts.size == 2 && parts.all { it.isNotBlank() }) parts[0].trim() to parts[1].trim()
            else null to base
        return FolderTrackEntity(
            documentId = f.path,
            uri = File(f.path).toURI().toString(),
            folder = f.folder,
            lastModified = f.lastModified,
            size = f.size,
            title = title,
            artist = artist,
            album = null,
            durationMs = 0,
            tagged = false,
        )
    }

    private fun FolderTrackEntity.toTrack() = Track(
        id = idFor(documentId),
        title = title,
        artist = artist ?: "Artista desconocido",
        album = album ?: "Álbum desconocido",
        albumId = album?.let { idFor("album:" + it.lowercase()) } ?: 0,
        durationMs = durationMs,
        folder = folder,
        uri = FileUri(URI(uri)),
    )

    private data class FileEntry(
        val path: String,
        val name: String,
        val folder: String,
        val lastModified: Long,
        val size: Long,
    )

    companion object {
        private const val TAG_BATCH = 16
        private const val TAG_THREADS = 4
        private const val MAX_DEPTH = 32
        private const val RESCAN_MS = 30_000L

        val AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "aac", "webm", "weba", "opus", "ogg", "oga", "flac", "wav", "mka", "wma",
        )

        private val YT_ID_SUFFIX = Regex("""\s*\[[A-Za-z0-9_-]{11}]$""")

        private fun String.isInside(dir: File): Boolean = startsWith(dir.path + File.separator)

        /**
         * Nombre de la carpeta que se muestra, como en Android: en la de descargas, "Eleuthera"
         * para las sueltas y el nombre de la playlist para las de sus subcarpetas; en las demás,
         * la ruta desde la carpeta añadida.
         */
        private fun folderLabel(file: File, root: File): String {
            val parent = file.parentFile ?: return root.name
            val rel = root.toPath().relativize(parent.toPath()).toString().replace('\\', '/')
            return if (root == DesktopPaths.musicDir) {
                rel.ifEmpty { "Eleuthera" }
            } else {
                listOf(root.name.ifEmpty { root.path }, rel).filter { it.isNotEmpty() }.joinToString("/")
            }
        }

        /** Id estable para playlists y cola: hash FNV-1a de 64 bits, en negativo como en Android. */
        fun idFor(key: String): Long = fnv(key) or Long.MIN_VALUE

        fun fnv(key: String): Long {
            var h = -0x340d631b7bdddcdbL   // 0xcbf29ce484222325
            for (ch in key) {
                h = h xor ch.code.toLong()
                h *= 0x100000001b3L
            }
            return h
        }
    }
}