package com.tuapp.eleuthera.data.playlist

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.tuapp.eleuthera.data.db.PlaylistDao
import com.tuapp.eleuthera.data.library.MediaLibrary
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Playlists en Android. Todo lo común está en [PlaylistStore] (:shared); aquí solo lo que
 * depende de Android: leer la imagen de portada elegida y la importación de carpetas antiguas.
 */
@Singleton
class PlaylistRepository @Inject constructor(
    private val dao: PlaylistDao,
    private val library: MediaLibrary,
    @ApplicationContext private val ctx: Context,
) : PlaylistStore(dao, library.observe(), File(ctx.filesDir, "covers")) {

    override suspend fun writeCover(image: Uri, dest: File, maxPx: Int) {
        val source = ImageDecoder.createSource(ctx.contentResolver, image)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > maxPx) {
                val scale = maxPx.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).toInt(),
                    (info.size.height * scale).toInt(),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        dest.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    /**
     * Convierte en playlists las carpetas de playlists descargadas antes de existir esta función.
     * Solo se ejecuta una vez, para no resucitar playlists que el usuario haya borrado.
     */
    suspend fun importDownloadedFoldersOnce() {
        val prefs = ctx.getSharedPreferences("playlists", Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_IMPORTED, false)) return
        library.scan(includeExtraFolders = false)
            .filter { it.folder != MediaLibrary.ROOT_FOLDER }
            .groupBy { it.folder }
            .forEach { (folder, tracks) ->
                tracks.forEachIndexed { i, track -> dao.addToNamed(folder, track.id, i) }
            }
        prefs.edit().putBoolean(KEY_IMPORTED, true).apply()
    }

    private companion object {
        const val KEY_IMPORTED = "folders_imported"
    }
}