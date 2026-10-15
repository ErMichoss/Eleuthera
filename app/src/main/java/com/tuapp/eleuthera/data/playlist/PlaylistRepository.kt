package com.tuapp.eleuthera.data.playlist

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.tuapp.eleuthera.data.db.PlaylistDao
import com.tuapp.eleuthera.data.db.PlaylistEntity
import com.tuapp.eleuthera.data.library.MediaLibrary
import com.tuapp.eleuthera.domain.model.Playlist
import com.tuapp.eleuthera.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/** Playlists: Room guarda qué canciones y en qué orden; MediaStore aporta sus datos. */
@Singleton
class PlaylistRepository @Inject constructor(
    private val dao: PlaylistDao,
    private val library: MediaLibrary,
    @ApplicationContext private val ctx: Context,
) {
    /** Todas las playlists con su tamaño, duración y carátulas para la portada. */
    fun playlists(): Flow<List<Playlist>> =
        combine(dao.observePlaylists(), dao.observeAllEntries(), library.observe()) { lists, entries, all ->
            val byId = all.associateBy { it.id }
            val byPlaylist = entries.groupBy { it.playlistId }
            lists.map { entity ->
                // Las canciones borradas del dispositivo se omiten.
                val tracks = byPlaylist[entity.id].orEmpty().mapNotNull { byId[it.trackId] }
                Playlist(
                    id = entity.id,
                    name = entity.name,
                    trackCount = tracks.size,
                    durationMs = tracks.sumOf { it.durationMs },
                    covers = tracks.take(4).map { it.uri },
                    customCover = entity.coverPath,
                )
            }
        }

    /** Canciones de la playlist en su orden. */
    fun tracks(playlistId: Long): Flow<List<Track>> =
        combine(dao.observeTrackIds(playlistId), library.observe()) { ids, all ->
            val byId = all.associateBy { it.id }
            ids.mapNotNull(byId::get)
        }

    suspend fun create(name: String): Long = dao.insert(PlaylistEntity(name = name.trim()))
    suspend fun rename(playlistId: Long, name: String) = dao.rename(playlistId, name.trim())
    suspend fun delete(playlistId: Long) {
        dao.coverPath(playlistId)?.let { File(it).delete() }
        dao.delete(playlistId)
    }

    /**
     * Copia la imagen elegida al almacenamiento privado, reducida a 800 px como máximo, y la
     * pone como portada. Cada cambio usa un nombre nuevo para que no se muestre la anterior en caché.
     */
    suspend fun setCover(playlistId: Long, image: Uri) = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(ctx.contentResolver, image)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > COVER_MAX_PX) {
                val scale = COVER_MAX_PX.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).toInt(),
                    (info.size.height * scale).toInt(),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val dir = File(ctx.filesDir, "covers").apply { mkdirs() }
        val file = File(dir, "${playlistId}_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        dao.coverPath(playlistId)?.let { File(it).delete() }
        dao.setCover(playlistId, file.absolutePath)
    }

    suspend fun clearCover(playlistId: Long) {
        dao.coverPath(playlistId)?.let { File(it).delete() }
        dao.setCover(playlistId, null)
    }

    /** Guarda el orden completo tras arrastrar. */
    suspend fun reorder(playlistId: Long, orderedTrackIds: List<Long>) =
        dao.replaceTracks(playlistId, orderedTrackIds)
    suspend fun add(playlistId: Long, trackId: Long) = dao.addTrack(playlistId, trackId)

    /** Añade varias canciones (un álbum, otra playlist...). Devuelve cuántas eran nuevas. */
    suspend fun addAll(playlistId: Long, trackIds: List<Long>): Int = dao.addTracks(playlistId, trackIds)
    suspend fun remove(playlistId: Long, trackId: Long) = dao.removeTrack(playlistId, trackId)

    /** Quita varias canciones de la playlist (no las borra del dispositivo). */
    suspend fun removeAll(playlistId: Long, trackIds: List<Long>) =
        trackIds.chunked(500).forEach { dao.removeTracks(playlistId, it) }

    /** Canción recién descargada de una playlist de YouTube: va a la playlist homónima. */
    suspend fun addDownloaded(playlistName: String, trackId: Long, position: Int) =
        dao.addToNamed(playlistName, trackId, position)

    /** Mueve la canción [trackId] una posición arriba (-1) o abajo (+1). */
    suspend fun move(playlistId: Long, trackId: Long, delta: Int) {
        val ids = dao.trackIds(playlistId).toMutableList()
        val from = ids.indexOf(trackId)
        val to = from + delta
        if (from < 0 || to !in ids.indices) return
        ids.add(to, ids.removeAt(from))
        dao.replaceTracks(playlistId, ids)
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
        const val COVER_MAX_PX = 800
    }
}