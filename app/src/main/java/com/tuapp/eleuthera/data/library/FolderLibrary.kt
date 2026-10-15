package com.tuapp.eleuthera.data.library

import android.content.Context
import android.database.ContentObserver
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.MediaStore
import android.util.Log
import com.tuapp.eleuthera.data.db.FolderTrackDao
import com.tuapp.eleuthera.data.db.FolderTrackEntity
import com.tuapp.eleuthera.data.settings.LibraryFolder
import com.tuapp.eleuthera.data.settings.LibrarySettings
import com.tuapp.eleuthera.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * Canciones de las carpetas añadidas en Ajustes. Recorre las carpetas con el permiso del
 * selector y se queda con todo lo que tenga extensión de audio, sin depender de MediaStore.
 *
 * 1. Listar la carpeta es barato: se hace en cada actualización.
 * 2. Los archivos nuevos o modificados salen al momento con lo que dice su nombre.
 * 3. Sus etiquetas se leen en segundo plano y se guardan en Room; la próxima vez no se abren.
 */
@Singleton
class FolderLibrary @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val settings: LibrarySettings,
    private val dao: FolderTrackDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** null hasta tener la primera lista (para no mostrar "biblioteca vacía" en falso). */
    private val _tracks = MutableStateFlow<List<Track>?>(null)
    val tracks: StateFlow<List<Track>?> = _tracks.asStateFlow()

    private val refreshRequests =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        scope.launch {
            // Lo que ya conocíamos sale enseguida; la sincronización lo corrige después.
            _tracks.value = dao.all().map { it.toTrack() }
            observeTriggers()
        }
    }

    /** Vuelve a mirar las carpetas (p. ej. tras borrar una canción). */
    fun refresh() {
        refreshRequests.tryEmit(Unit)
    }

    @OptIn(FlowPreview::class)
    private suspend fun observeTriggers() {
        // Cualquier cambio en el almacenamiento suele indicar archivos nuevos: se agrupan
        // los avisos seguidos (p. ej. durante una descarga) para no listar a cada momento.
        val triggers = merge(refreshRequests, storageChanges().debounce(1_500))
            .onStart { emit(Unit) }
        combine(settings.folders, triggers) { folders, _ -> folders }
            .collectLatest { folders -> sync(folders) }
    }

    private suspend fun sync(folders: List<LibraryFolder>) {
        var complete = true
        val files = folders.flatMap { folder ->
            runCatching { listAudio(folder) }
                .onFailure { complete = false; Log.w(TAG, "No se pudo leer ${folder.label}", it) }
                .getOrDefault(emptyList())
        }.distinctBy { it.documentId }   // carpetas que se solapan (Music y Music/Rock)

        val cache = dao.all().associateBy { it.documentId }

        // Si alguna carpeta falló (permiso retirado, SD sacada) no borramos nada del caché.
        if (complete) {
            val present = files.mapTo(HashSet()) { it.documentId }
            val gone = cache.keys.filter { it !in present }
            gone.chunked(500).forEach { dao.delete(it) }
        }

        val entries = files.map { f ->
            val cached = cache[f.documentId]
            if (cached != null && cached.lastModified == f.lastModified && cached.size == f.size) {
                cached.copy(uri = f.uri, folder = f.folder)
            } else {
                fromFileName(f)
            }
        }
        dao.upsert(entries.filter { cache[it.documentId] != it })

        val current = LinkedHashMap<String, FolderTrackEntity>().apply {
            entries.forEach { put(it.documentId, it) }
        }
        publish(current.values)

        // Etiquetas en segundo plano, en tandas, actualizando la lista según avanzan.
        entries.filter { !it.tagged }.chunked(TAG_BATCH).forEach { batch ->
            coroutineContext.ensureActive()
            val tagged = batch.map(::readTags)
            dao.upsert(tagged)
            tagged.forEach { current[it.documentId] = it }
            publish(current.values)
        }
    }

    private fun publish(entries: Collection<FolderTrackEntity>) {
        _tracks.value = entries.map { it.toTrack() }
    }

    /** Recorre la carpeta y sus subcarpetas devolviendo los archivos de audio. */
    private fun listAudio(folder: LibraryFolder): List<FileEntry> {
        val tree = folder.treeUri
        val out = mutableListOf<FileEntry>()
        val pending = ArrayDeque<String>().apply { add(DocumentsContract.getTreeDocumentId(tree)) }

        while (pending.isNotEmpty()) {
            val parentId = pending.removeFirst()
            if (isAppFolder(parentId)) continue   // Music/Eleuthera ya la lee MediaLibrary
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            ctx.contentResolver.query(children, PROJECTION, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val name = c.getString(1)
                    // Se saltan los ocultos (.thumbnails, .nomedia...).
                    if (id != null && name != null && !name.startsWith(".")) {
                        if (c.getString(2) == Document.MIME_TYPE_DIR) {
                            pending.add(id)
                        } else if (name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS) {
                            out += FileEntry(
                                documentId = id,
                                uri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(),
                                name = name,
                                folder = LibrarySettings.labelOf(parentId),
                                lastModified = c.getLong(3),
                                size = c.getLong(4),
                            )
                        }
                    }
                }
            }
        }
        return out
    }

    private fun readTags(e: FolderTrackEntity): FolderTrackEntity {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, Uri.parse(e.uri))
            fun key(k: Int) = r.extractMetadata(k)?.trim()?.takeIf { it.isNotEmpty() }
            e.copy(
                title = key(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: e.title,
                artist = key(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: key(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) ?: e.artist,
                album = key(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: e.album,
                durationMs = key(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    ?: e.durationMs,
                tagged = true,
            )
        } catch (t: Exception) {
            // Archivo dañado o formato raro: se queda con el nombre y no se reintenta.
            e.copy(tagged = true)
        } finally {
            r.release()
        }
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
            documentId = f.documentId,
            uri = f.uri,
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
        albumId = 0,
        durationMs = durationMs,
        folder = folder,
        uri = Uri.parse(uri),
    )

    /** Avisa cuando cambia algo en el almacenamiento compartido (archivos nuevos, borrados...). */
    private fun storageChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        ctx.contentResolver.registerContentObserver(uri, true, observer)
        awaitClose { ctx.contentResolver.unregisterContentObserver(observer) }
    }

    private data class FileEntry(
        val documentId: String,
        val uri: String,
        val name: String,
        val folder: String,
        val lastModified: Long,
        val size: Long,
    )

    companion object {
        private const val TAG = "FolderLibrary"
        private const val TAG_BATCH = 10

        val AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "aac", "webm", "weba", "opus", "ogg", "oga", "flac", "wav", "mka", "wma",
        )

        private val YT_ID_SUFFIX = Regex("""\s*\[[A-Za-z0-9_-]{11}]$""")

        private val PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_SIZE,
        )

        private val APP_FOLDER_ID = "primary:${MediaLibrary.BASE_PATH}"

        private fun isAppFolder(documentId: String) =
            documentId == APP_FOLDER_ID || documentId.startsWith("$APP_FOLDER_ID/")

        /**
         * Id estable para playlists y cola: hash FNV-1a de 64 bits del documento, en negativo
         * para no chocar nunca con los _ID de MediaStore (que son positivos).
         */
        fun idFor(documentId: String): Long {
            var h = -0x340d631b7bdddcdbL   // 0xcbf29ce484222325
            for (ch in documentId) {
                h = h xor ch.code.toLong()
                h *= 0x100000001b3L
            }
            return h or Long.MIN_VALUE
        }

        fun isFolderTrack(id: Long) = id < 0
    }
}