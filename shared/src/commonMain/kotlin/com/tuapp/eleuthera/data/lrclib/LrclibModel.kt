package com.tuapp.eleuthera.data.lrclib

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LrclibLyrics(
    @SerialName("id") val id: Long = 0,
    @SerialName("name") val name: String? = null,
    @SerialName("trackName") val trackName: String? = null,
    @SerialName("artistName") val artistName: String? = null,
    @SerialName("albumName") val albumName: String? = null,
    @SerialName("duration") val durationSeconds: Double? = null,
    @SerialName("instrumental") val instrumental: Boolean? = null,
    @SerialName("plainLyrics") val plainLyrics: String? = null,
    @SerialName("syncedLyrics") val syncedLyrics: String? = null,
    @SerialName("hasSyncedLyrics") val hasSyncedLyrics: Boolean? = null,
    @SerialName("hasPlainLyrics") val hasPlainLyrics: Boolean? = null,
    @SerialName("hasWordSync") val hasWordSync: Boolean? = null,
)
