package com.tuapp.eleuthera.ui.library

import com.tuapp.eleuthera.domain.model.Track

/** Acciones sobre canciones; las conecta la raíz de cada app con el reproductor y las playlists. */
data class TrackActions(
    /** Lista visible e índice pulsado: se reproduce desde ahí y el resto queda en cola. */
    val onPlay: (tracks: List<Track>, index: Int) -> Unit = { _, _ -> },
    /** Activa el aleatorio y reproduce la lista empezando por una canción al azar. */
    val onShuffle: (tracks: List<Track>) -> Unit = {},
    val onPlayNext: (Track) -> Unit = {},
    val onAddToQueue: (Track) -> Unit = {},
    val onAddToPlaylist: (Track) -> Unit = {},
    /** Varias canciones (una playlist, un álbum...) justo después de la actual. */
    val onPlayNextAll: (List<Track>) -> Unit = {},
    /** Varias canciones al final de la cola del usuario. */
    val onAddAllToQueue: (List<Track>) -> Unit = {},
    /**
     * Varias canciones a una playlist. [label] es lo que se añade ("Nevermind") y se propone
     * como nombre de playlist nueva; null para una selección suelta.
     */
    val onAddAllToPlaylist: (label: String?, tracks: List<Track>) -> Unit = { _, _ -> },
)