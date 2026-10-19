package com.tuapp.eleuthera.data.lrclib

import com.google.gson.annotations.SerializedName

data class LrclibLyrics(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String?,
    @SerializedName("trackName") val trackName: String?,
    @SerializedName("artistName") val artistName: String?,
    @SerializedName("albumName") val albumName: String?,
    @SerializedName("duration") val durationSeconds: Double?,
    @SerializedName("instrumental") val instrumental: Boolean?,
    @SerializedName("plainLyrics") val plainLyrics: String?,
    @SerializedName("syncedLyrics") val syncedLyrics: String?,
    @SerializedName("hasSyncedLyrics") val hasSyncedLyrics: Boolean?,
    @SerializedName("hasPlainLyrics") val hasPlainLyrics: Boolean?,
    @SerializedName("hasWordSync") val hasWordSync: Boolean?,
)
