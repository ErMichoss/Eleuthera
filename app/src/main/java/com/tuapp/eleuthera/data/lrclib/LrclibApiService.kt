package com.tuapp.eleuthera.data.lrclib

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface LrclibApiService {

    @GET("api/search")
    suspend fun searchLyrics(
        @Query("q") query: String,
    ): List<LrclibLyrics>

    @GET("api/search")
    suspend fun searchLyricsByMetadata(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String? = null,
    ): List<LrclibLyrics>

    @GET("api/get")
    suspend fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String? = null,
        @Query("duration") durationSeconds: Double? = null,
    ): LrclibLyrics

    @GET("api/get/{id}")
    suspend fun getLyricsById(
        @Path("id") id: Long,
    ): LrclibLyrics
}
