package com.tuapp.eleuthera.data.playlist

import com.tuapp.eleuthera.data.db.PlaylistDao
import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.platform.FileUri
import com.tuapp.eleuthera.platform.PlatformUri
import com.tuapp.eleuthera.playback.Ffmpeg
import java.io.File
import kotlinx.coroutines.flow.Flow

/** Playlists en escritorio: todo es [PlaylistStore]; la portada se reduce con ffmpeg. */
class DesktopPlaylistStore(
    dao: PlaylistDao,
    library: Flow<List<Track>>,
    coversDir: File,
) : PlaylistStore(dao, library, coversDir) {

    override suspend fun writeCover(image: PlatformUri, dest: File, maxPx: Int) {
        val source = (image as? FileUri)?.file ?: error("Imagen no válida")
        if (!Ffmpeg.extractCover(source, dest, maxPx)) error("No se pudo leer la imagen")
    }
}