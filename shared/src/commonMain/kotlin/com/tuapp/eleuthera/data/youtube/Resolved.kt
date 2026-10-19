package com.tuapp.eleuthera.data.youtube

import com.tuapp.eleuthera.domain.model.RemoteTrack

/** Resultado de resolver una URL: un vídeo suelto o una playlist expandida. */
data class Resolved(val playlistTitle: String?, val tracks: List<RemoteTrack>)