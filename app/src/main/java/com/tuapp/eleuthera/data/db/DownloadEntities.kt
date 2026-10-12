package com.tuapp.eleuthera.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/** Qué archivo de la biblioteca (_ID de MediaStore) corresponde a cada vídeo de YouTube. */
@Entity(tableName = "video_tracks")
data class VideoTrackEntity(
    @PrimaryKey val videoId: String,
    val trackId: Long,
)

/**
 * Petición de añadir un vídeo a una playlist. Se guarda antes de descargar y se resuelve
 * cuando el archivo existe, así varias playlists pueden pedir la misma canción a la vez.
 */
@Entity(tableName = "playlist_requests", primaryKeys = ["videoId", "playlistName"])
data class PlaylistRequestEntity(
    val videoId: String,
    val playlistName: String,
    val position: Int,
)

@Dao
interface DownloadDao {
    @Query("SELECT trackId FROM video_tracks WHERE videoId = :videoId")
    suspend fun trackIdFor(videoId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setTrack(entity: VideoTrackEntity)

    @Query("DELETE FROM video_tracks WHERE videoId = :videoId")
    suspend fun forget(videoId: String)

    /** Si la misma playlist ya pidió ese vídeo, se ignora (evita repetidos en una playlist). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addRequest(request: PlaylistRequestEntity)

    @Query("SELECT * FROM playlist_requests WHERE videoId = :videoId")
    suspend fun requestsFor(videoId: String): List<PlaylistRequestEntity>

    @Query("DELETE FROM playlist_requests WHERE videoId = :videoId")
    suspend fun clearRequests(videoId: String)
}