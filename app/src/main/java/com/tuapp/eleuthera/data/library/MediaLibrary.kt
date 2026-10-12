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
import android.provider.MediaStore
import com.tuapp.eleuthera.data.settings.LibrarySettings
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Acceso a la música del dispositivo a través de MediaStore. */
@Singleton
class MediaLibrary @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val librarySettings: LibrarySettings,
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
     * Borra la canción del dispositivo. Devuelve null si ya está borrada, o un IntentSender
     * cuando Android exige que el usuario lo confirme (archivos que la app no creó en esta
     * instalación, p. ej. tras reinstalarla).
     */
    suspend fun delete(track: Track): IntentSender? = withContext(Dispatchers.IO) {
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

    /** Lista la música de la carpeta de la app. MediaStore ya lee las etiquetas ID3. */
    suspend fun scan(includeExtraFolders: Boolean = true): List<Track> = withContext(Dispatchers.IO) {
        // Música/Eleuthera siempre; además, las carpetas añadidas en Ajustes.
        val roots = buildList {
            add("$BASE_PATH/")
            if (includeExtraFolders) addAll(librarySettings.folders.first())
        }
        val selection = roots.joinToString(" OR ", prefix = "(", postfix = ")") {
            "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        } + " AND ${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val args = roots.map { "$it%" }.toTypedArray()

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
        combine(changes(), librarySettings.folders) { _, _ -> }
            .conflate()
            .map { scan() }
            .flowOn(Dispatchers.IO)

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