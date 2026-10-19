package com.tuapp.eleuthera.data.library

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.tuapp.eleuthera.data.db.PlaylistDao
import com.tuapp.eleuthera.domain.model.AudioFormat
import com.tuapp.eleuthera.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Biblioteca completa: Música/Eleuthera (lo descargado por la app) a través de MediaStore,
 * más las carpetas añadidas en Ajustes, que lee [FolderLibrary] recorriéndolas directamente.
 */
@Singleton
class MediaLibrary @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val folderLibrary: FolderLibrary,
    private val playlistDao: PlaylistDao,
) {

    private val collection: Uri =
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    /**
     * Mueve un MP3 temporal a Music/Eleuthera (o Music/Eleuthera/<playlist>) para que lo vean
     * esta y otras apps. No necesita permisos de almacenamiento en Android 10+.
     */
    suspend fun publish(file: File, playlist: String? = null): Uri = withContext(Dispatchers.IO) {
        val resolver = ctx.contentResolver
        val folder = playlist?.replace(Regex("""[\\/:*?"<>|]"""), "_")?.trim()?.ifEmpty { null }
        val path = listOfNotNull(BASE_PATH, folder).joinToString("/")

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
            put(
                MediaStore.Audio.Media.MIME_TYPE,
                AudioFormat.entries.firstOrNull { it.extension == file.extension }?.mimeType
                    ?: "audio/mpeg",
            )
            put(MediaStore.Audio.Media.RELATIVE_PATH, path)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("No se pudo crear el archivo en Música")
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: error("No se pudo escribir en Música")
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)   // no dejar entradas a medias
            throw e
        } finally {
            file.parentFile?.deleteRecursively()
        }
        uri
    }

    /**
     * Sustituye el original por un M4A en la misma ubicación y restaura audio/nombre/MIME si falla.
     * En carpetas SAF el cambio de nombre puede devolver una URI nueva.
     */
    suspend fun replaceAudio(track: Track, replacement: File, backup: File, newName: String): Track =
        withContext(Dispatchers.IO) {
            val resolver = ctx.contentResolver
            val oldName = displayName(track.uri)
                ?: error("No se pudo determinar el nombre del archivo original.")
            val oldMimeType = resolver.getType(track.uri)
                ?: oldName.substringAfterLast('.', "").lowercase()
                    .let { android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
                ?: "audio/mpeg"
            var targetUri = track.uri
            var targetTrackId = track.id
            var renamed = false
            var contentWriteStarted = false
            try {
                if (FolderLibrary.isFolderTrack(track.id)) {
                    if (newName != oldName) {
                        targetUri = DocumentsContract.renameDocument(resolver, track.uri, newName)
                            ?: error("No se pudo cambiar la extensión del archivo a .m4a.")
                        renamed = true
                    }
                } else {
                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.DISPLAY_NAME, newName)
                        put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    }
                    check(resolver.update(track.uri, values, null, null) > 0) {
                        "No se pudo actualizar el nombre y el formato del archivo."
                    }
                    renamed = true
                }

                contentWriteStarted = true
                resolver.openOutputStream(targetUri, "wt")?.use { output ->
                    replacement.inputStream().use { input -> input.copyTo(output) }
                } ?: error("El proveedor no permite modificar este archivo.")

                if (FolderLibrary.isFolderTrack(track.id)) {
                    val oldDocumentId = DocumentsContract.getDocumentId(track.uri)
                    val newDocumentId = DocumentsContract.getDocumentId(targetUri)
                    if (oldDocumentId != newDocumentId) {
                        targetTrackId = FolderLibrary.idFor(newDocumentId)
                        playlistDao.replaceTrackId(
                            track.id,
                            targetTrackId,
                        )
                    }
                }
            } catch (failure: Exception) {
                var rollbackFailure: Exception? = null
                if (contentWriteStarted) {
                    try {
                        resolver.openOutputStream(targetUri, "wt")?.use { output ->
                            backup.inputStream().use { input -> input.copyTo(output) }
                        } ?: error("No se pudo restaurar el audio original.")
                    } catch (e: Exception) {
                        rollbackFailure = e
                    }
                }
                if (renamed) {
                    try {
                        if (FolderLibrary.isFolderTrack(track.id)) {
                            DocumentsContract.renameDocument(resolver, targetUri, oldName)
                                ?: error("No se pudo restaurar el nombre original.")
                        } else {
                            val values = ContentValues().apply {
                                put(MediaStore.Audio.Media.DISPLAY_NAME, oldName)
                                put(MediaStore.Audio.Media.MIME_TYPE, oldMimeType)
                            }
                            check(resolver.update(track.uri, values, null, null) > 0) {
                                "No se pudo restaurar el nombre y el formato originales."
                            }
                        }
                    } catch (e: Exception) {
                        if (rollbackFailure == null) rollbackFailure = e else rollbackFailure.addSuppressed(e)
                    }
                }
                if (rollbackFailure != null) {
                    failure.addSuppressed(rollbackFailure)
                    throw IllegalStateException(
                        "Falló el recorte y no se pudo restaurar completamente el archivo original.",
                        failure,
                    )
                }
                throw failure
            } finally {
                if (FolderLibrary.isFolderTrack(track.id)) folderLibrary.refresh()
            }

            resolver.notifyChange(targetUri, null)
            track.copy(id = targetTrackId, uri = targetUri)
        }

    /** Nombre físico del audio. */
    suspend fun displayName(uri: Uri): String? = withContext(Dispatchers.IO) {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }

    /**
     * Borra la canción del dispositivo. Devuelve null si ya está borrada, o un IntentSender
     * cuando Android exige que el usuario lo confirme (archivos que la app no creó en esta
     * instalación, p. ej. tras reinstalarla).
     */
    suspend fun delete(track: Track): IntentSender? = withContext(Dispatchers.IO) {
        if (FolderLibrary.isFolderTrack(track.id)) {
            // Archivo de una carpeta añadida: se borra con el permiso de la carpeta.
            if (!DocumentsContract.deleteDocument(ctx.contentResolver, track.uri)) {
                error("No se pudo borrar ${track.title}")
            }
            folderLibrary.refresh()
            return@withContext null
        }
        try {
            ctx.contentResolver.delete(track.uri, null, null)
            null
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                MediaStore.createDeleteRequest(ctx.contentResolver, listOf(track.uri)).intentSender
            } else {
                (e as? RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
                    ?: throw e
            }
        }
    }

    /** true si la canción sigue en el dispositivo (no se ha borrado ni movido). */
    suspend fun exists(trackId: Long): Boolean = withContext(Dispatchers.IO) {
        ctx.contentResolver.query(
            ContentUris.withAppendedId(collection, trackId),
            arrayOf(MediaStore.Audio.Media._ID), null, null, null,
        )?.use { it.count > 0 } ?: false
    }

    /** Lista la biblioteca; con [includeExtraFolders] = false, solo Música/Eleuthera. */
    suspend fun scan(includeExtraFolders: Boolean = true): List<Track> {
        val own = scanAppFolder()
        if (!includeExtraFolders) return own
        return merged(own, folderLibrary.tracks.filterNotNull().first())
    }

    /** Música/Eleuthera vía MediaStore, que ya lee las etiquetas ID3. */
    private suspend fun scanAppFolder(): List<Track> = withContext(Dispatchers.IO) {
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("$BASE_PATH/%")

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.RELATIVE_PATH,
        )
        buildList {
            ctx.contentResolver.query(
                collection,
                projection,
                selection,
                args,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    add(
                        Track(
                            id = id,
                            title = c.getString(1).orEmpty(),
                            artist = c.getString(2).known() ?: "Artista desconocido",
                            album = c.getString(3).known() ?: "Álbum desconocido",
                            albumId = c.getLong(4),
                            durationMs = c.getLong(5),
                            // "Music/Eleuthera/Mi playlist/" -> "Mi playlist"; la raíz -> "Eleuthera"
                            folder = c.getString(6).orEmpty().trimEnd('/')
                                .removePrefix(BASE_PATH).trimStart('/').ifEmpty { ROOT_FOLDER },
                            uri = ContentUris.withAppendedId(collection, id),
                        )
                    )
                }
            }
        }
    }

    /** Emite la biblioteca y vuelve a emitirla cada vez que cambia (p. ej. termina una descarga). */
    fun observe(): Flow<List<Track>> =
        combine(
            changes().conflate().map { scanAppFolder() },
            folderLibrary.tracks.filterNotNull(),
            { own, folders -> merged(own, folders) },
        ).flowOn(Dispatchers.IO)

    /** Vuelve a mirar las carpetas añadidas (al abrir la biblioteca, p. ej.). */
    fun refreshFolders() = folderLibrary.refresh()

    private fun merged(own: List<Track>, folders: List<Track>): List<Track> =
        if (folders.isEmpty()) own
        else (own + folders).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

    /** Avisa cada vez que cambia algo en MediaStore. */
    private fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        ctx.contentResolver.registerContentObserver(collection, true, observer)
        trySend(Unit)
        awaitClose { ctx.contentResolver.unregisterContentObserver(observer) }
    }

    private fun String?.known(): String? =
        this?.takeUnless { it.isBlank() || it == MediaStore.UNKNOWN_STRING }

    companion object {
        const val ROOT_FOLDER = "Eleuthera"
        /** Carpeta base de la biblioteca: Music/Eleuthera */
        val BASE_PATH = "${Environment.DIRECTORY_MUSIC}/$ROOT_FOLDER"
    }
}