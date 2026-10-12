package com.tuapp.eleuthera.domain.model

import android.net.Uri

/** Una canción que ya está en el dispositivo. */
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    /** Nombre de la playlist descargada, o "Eleuthera" para las canciones sueltas. */
    val folder: String,
    val uri: Uri,
)
