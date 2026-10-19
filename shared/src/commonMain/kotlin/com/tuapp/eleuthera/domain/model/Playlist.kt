package com.tuapp.eleuthera.domain.model

import com.tuapp.eleuthera.platform.PlatformUri

/** Playlist lista para pintar: datos, tamaño y canciones para la portada. */
data class Playlist(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val durationMs: Long,
    /** Hasta 4 canciones cuyas carátulas forman el mosaico de la portada. */
    val covers: List<PlatformUri>,
    /** Imagen elegida por el usuario; si existe, sustituye al mosaico. */
    val customCover: String? = null,
)