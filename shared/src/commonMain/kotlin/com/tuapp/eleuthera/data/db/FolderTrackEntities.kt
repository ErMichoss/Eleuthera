package com.tuapp.eleuthera.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * Canción de una carpeta añadida en Ajustes. Guarda las etiquetas ya leídas para no volver
 * a abrir el archivo mientras no cambie (misma fecha de modificación y mismo tamaño).
 */
@Entity(tableName = "folder_tracks")
data class FolderTrackEntity(
    /** Id del documento, p. ej. "primary:Download/Musica/cancion.webm". Único por archivo. */
    @PrimaryKey val documentId: String,
    /** URI con la que se abre el archivo (lleva el permiso de la carpeta). */
    val uri: String,
    val folder: String,
    val lastModified: Long,
    val size: Long,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    /** false mientras solo tenemos lo sacado del nombre del archivo. */
    val tagged: Boolean,
)

@Dao
interface FolderTrackDao {
    @Query("SELECT * FROM folder_tracks")
    suspend fun all(): List<FolderTrackEntity>

    @Upsert
    suspend fun upsert(items: List<FolderTrackEntity>)

    @Query("DELETE FROM folder_tracks WHERE documentId IN (:ids)")
    suspend fun delete(ids: List<String>)
}