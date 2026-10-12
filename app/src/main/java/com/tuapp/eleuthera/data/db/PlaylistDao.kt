package com.tuapp.eleuthera.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    abstract fun observePlaylists(): Flow<List<PlaylistEntity>>

    /** Todas las filas de todas las playlists, ya ordenadas dentro de cada una. */
    @Query("SELECT * FROM playlist_tracks ORDER BY playlistId, position")
    abstract fun observeAllEntries(): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position")
    abstract fun observeTrackIds(playlistId: Long): Flow<List<Long>>

    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position")
    abstract suspend fun trackIds(playlistId: Long): List<Long>

    @Query("SELECT id FROM playlists WHERE name = :name LIMIT 1")
    abstract suspend fun findIdByName(name: String): Long?

    @Insert
    abstract suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    abstract suspend fun rename(playlistId: Long, name: String)

    @Query("UPDATE playlists SET coverPath = :path WHERE id = :playlistId")
    abstract suspend fun setCover(playlistId: Long, path: String?)

    @Query("SELECT coverPath FROM playlists WHERE id = :playlistId")
    abstract suspend fun coverPath(playlistId: Long): String?

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    abstract suspend fun delete(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTracks(items: List<PlaylistTrackEntity>)

    @Query("SELECT MAX(position) FROM playlist_tracks WHERE playlistId = :playlistId")
    abstract suspend fun maxPosition(playlistId: Long): Int?

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    abstract suspend fun removeTrack(playlistId: Long, trackId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    abstract suspend fun clear(playlistId: Long)

    /** Añade al final; si la canción ya estaba, no hace nada. */
    @Transaction
    open suspend fun addTrack(playlistId: Long, trackId: Long) {
        val next = (maxPosition(playlistId) ?: -1) + 1
        insertTracks(listOf(PlaylistTrackEntity(playlistId, trackId, next)))
    }

    /**
     * Añade a la playlist llamada [name], creándola si no existe. Al ser una transacción,
     * varias descargas simultáneas de la misma playlist no la crean por duplicado.
     */
    @Transaction
    open suspend fun addToNamed(name: String, trackId: Long, position: Int) {
        val id = findIdByName(name) ?: insert(PlaylistEntity(name = name))
        insertTracks(listOf(PlaylistTrackEntity(id, trackId, position)))
    }

    /** Reescribe el orden completo de la playlist. */
    @Transaction
    open suspend fun replaceTracks(playlistId: Long, orderedTrackIds: List<Long>) {
        clear(playlistId)
        insertTracks(orderedTrackIds.mapIndexed { i, id -> PlaylistTrackEntity(playlistId, id, i) })
    }
}
