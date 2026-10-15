package com.tuapp.eleuthera.data.metadata

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import com.tuapp.eleuthera.data.playlist.PlaylistRepository
import com.tuapp.eleuthera.domain.model.Playlist
import com.tuapp.eleuthera.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Datos de metadatos completos de una canción, incluyendo el álbum y la imagen de portada. */
data class TrackMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val coverArtwork: ByteArray?,
    val hasCoverArt: Boolean,
) {
    val coverBitmap: Bitmap?
        get() = coverArtwork?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TrackMetadata
        return title == other.title &&
                artist == other.artist &&
                album == other.album &&
                albumId == other.albumId &&
                hasCoverArt == other.hasCoverArt &&
                (coverArtwork?.contentEquals(other.coverArtwork ?: byteArrayOf()) ?: (other.coverArtwork == null))
    }

    //Override a hashcode por uso de variable de tipo Array
    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + albumId.hashCode()
        result = 31 * result + hasCoverArt.hashCode()
        result = 31 * result + (coverArtwork?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Encargado de obtener y actualizar metadatos de canciones (álbum, portada, artista, título)
 * y de filtrar canciones a las que les falta imagen de portada o metadatos en una playlist.
 */
@Singleton
class MetaDataManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val playlistRepository: PlaylistRepository,
) {

    /**
     * Dada una canción [Track], recupera sus metadatos detallados:
     * el álbum del que viene, artista, título y la imagen de portada incrustada o de MediaStore.
     */
    suspend fun getTrackMetadata(track: Track): TrackMetadata = withContext(Dispatchers.IO) {
        var title = track.title
        var artist = track.artist
        var album = track.album
        var artworkBytes: ByteArray? = null

        runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(ctx, track.uri)
                val retTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val retArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val retAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)

                if (!retTitle.isNullOrBlank()) title = retTitle
                if (!retArtist.isNullOrBlank()) artist = retArtist
                if (!retAlbum.isNullOrBlank()) album = retAlbum

                artworkBytes = retriever.embeddedPicture
            }
        }

        // Si no se obtuvo portada incrustada, buscar portada del álbum en MediaStore
        if (artworkBytes == null && track.albumId > 0) {
            artworkBytes = loadAlbumArtworkFromMediaStore(track.albumId)
        }

        TrackMetadata(
            title = title,
            artist = artist,
            album = album,
            albumId = track.albumId,
            coverArtwork = artworkBytes,
            hasCoverArt = artworkBytes != null && artworkBytes.isNotEmpty(),
        )
    }

    /**
     * Dada una canción [Track], devuelve de qué álbum proviene y su imagen de portada.
     */
    suspend fun getAlbumCoverInfo(track: Track): Pair<String, ByteArray?> {
        val metadata = getTrackMetadata(track)
        return Pair(metadata.album, metadata.coverArtwork)
    }

    /**
     * Comprueba (exists) si una canción tiene imagen de portada.
     */
    suspend fun existsCoverArt(track: Track): Boolean {
        return getTrackMetadata(track).hasCoverArt
    }

    /**
     * Comprueba si a una canción le faltan metadatos (portada, título, artista o álbum desconocido/vacío).
     */
    suspend fun isMissingMetadata(track: Track): Boolean {
        val meta = getTrackMetadata(track)
        val missingText = meta.title.isBlank() ||
                meta.artist.isBlank() ||
                meta.artist.equals("<unknown>", ignoreCase = true) ||
                meta.artist.equals("Desconocido", ignoreCase = true) ||
                meta.album.isBlank() ||
                meta.album.equals("<unknown>", ignoreCase = true) ||
                meta.album.equals("Desconocido", ignoreCase = true)
        return !meta.hasCoverArt || missingText
    }

    /**
     * Permite hacer set/modificación de los metadatos (título, artista, álbum) de una canción en MediaStore.
     */
    suspend fun setMetadata(
        track: Track,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val values = ContentValues()
            title?.let { values.put(MediaStore.Audio.Media.TITLE, it) }
            artist?.let { values.put(MediaStore.Audio.Media.ARTIST, it) }
            album?.let { values.put(MediaStore.Audio.Media.ALBUM, it) }

            if (values.size() > 0) {
                val rows = ctx.contentResolver.update(track.uri, values, null, null)
                rows > 0
            } else false
        }.getOrDefault(false)
    }

    /**
     * Dada una playlist (por su ID), devuelve una lista filtrada únicamente con las canciones
     * a las que les faltan metadatos o imagen de portada.
     */
    suspend fun getTracksWithMissingMetadata(playlistId: Long): List<Track> = withContext(Dispatchers.IO) {
        val tracks = playlistRepository.tracks(playlistId).first()
        getTracksWithMissingMetadata(tracks)
    }

    /**
     * Dada una playlist ([Playlist]), devuelve en una lista sólo las canciones a las que les faltan metadatos.
     */
    suspend fun getTracksWithMissingMetadata(playlist: Playlist): List<Track> =
        getTracksWithMissingMetadata(playlist.id)

    /**
     * Dada una lista de canciones, devuelve filtradas sólo las que carecen de portada o metadatos.
     */
    suspend fun getTracksWithMissingMetadata(tracks: List<Track>): List<Track> = withContext(Dispatchers.IO) {
        tracks.filter { track -> isMissingMetadata(track) }
    }

    /** Carga portada de álbum almacenada en MediaStore a través de su albumId. */
    private fun loadAlbumArtworkFromMediaStore(albumId: Long): ByteArray? {
        val albumArtUri = Uri.parse("content://media/external/audio/albumart")
        val uri = ContentUris.withAppendedId(albumArtUri, albumId)
        return runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes()
            }
        }.getOrNull()
    }
}
