package com.tuapp.eleuthera.domain.model

import kotlinx.serialization.Serializable

/** Una canción leída de un archivo exportado de Spotify, Qobuz u otro servicio. */
@Serializable
data class ImportedTrack(
    val title: String,
    val artist: String = "",
    /** Duración original en segundos, si el archivo la trae. Ayuda a elegir la versión correcta. */
    val durationSec: Long? = null,
    /** Playlist de origen, si el archivo contiene varias. */
    val playlist: String? = null,
) {
    val label: String get() = if (artist.isBlank()) title else "$artist - $title"
}