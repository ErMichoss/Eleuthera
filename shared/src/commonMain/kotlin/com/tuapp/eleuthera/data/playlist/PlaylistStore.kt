package com.tuapp.eleuthera.data.playlist

import com.tuapp.eleuthera.data.db.PlaylistDao
import com.tuapp.eleuthera.data.db.PlaylistEntity
import com.tuapp.eleuthera.domain.model.Playlist
import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.platform.PlatformUri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/**
 * Playlists, igual en Android y escritorio: Room guarda qué canciones y en qué orden, y
 * [library] (la biblioteca de cada plataforma) aporta sus datos.
 *
 * Cada plataforma solo pone cómo convertir la imagen elegida en portada ([writeCover]).
 * En Android es PlaylistRepository; en escritorio, DesktopPlaylistStore.
 */
abstract class PlaylistStore(
    private val dao: PlaylistDao,
    private val library: Flow<List<Track>>,
    /** Carpeta privada donde se guardan las portadas elegidas por el usuario. */
    private val coversDir: File,
) {
    /**
     * Guarda en [dest] (jpg) la imagen [image] reducida a [maxPx] px como mucho.
     * Lanza excepción si no se puede leer.
     */
    protected abstract suspend fun writeCover(image: PlatformUri, dest: File, maxPx: Int)

    /** Todas las playlists con su tamaño, duración y carátulas para la portada. */
    fun playlists(): Flow<List<Playlist>> =
        combine(dao.observePlaylists(), dao.observeAllEntries(), library) { lists, entries, all ->
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
        combine(dao.observeTrackIds(playlistId), library) { ids, all ->
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
    suspend fun setCover(playlistId: Long, image: PlatformUri) {
        val file = withContext(Dispatchers.IO) {
            coversDir.mkdirs()
            File(coversDir, "${playlistId}_${System.currentTimeMillis()}.jpg").also {
                writeCover(image, it, COVER_MAX_PX)
            }
        }
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

    /**
     * Canción recién descargada que va a la playlist [playlistName] (se crea si no existe): la
     * de YouTube con el mismo nombre, en su [position], o una elegida por el usuario con
     * [APPEND] (al final).
     */
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

    companion object {
        /** Posición para [addDownloaded]: al final de la playlist. */
        const val APPEND = -1
        private const val COVER_MAX_PX = 800
    }
}