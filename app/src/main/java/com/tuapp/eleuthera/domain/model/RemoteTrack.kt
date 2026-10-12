package com.tuapp.eleuthera.domain.model

/** Un resultado de YouTube todavía sin descargar. */
data class RemoteTrack(
    val id: String,
    val title: String,
    val author: String,
    val durationSec: Long,
    val url: String,
)
