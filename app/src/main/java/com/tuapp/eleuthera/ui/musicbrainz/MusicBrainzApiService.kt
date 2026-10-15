package com.tuapp.eleuthera.ui.musicbrainz

import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface MusicBrainzApiService {

    @Headers("User-Agent: MusicBrainzExplorerAndroid/1.0.0 (contacto@ejemplo.com)")
    @GET("ws/2/recording")
    suspend fun searchRecordings(
        @Query("query") query: String,
        @Query("limit") limit: Int,
        @Query("fmt") format: String = "json"
    ): RecordingSearchResponse

    @Headers("User-Agent: MusicBrainzExplorerAndroid/1.0.0 (contacto@ejemplo.com)")
    @GET("ws/2/release")
    suspend fun searchReleases(
        @Query("query") query: String,
        @Query("limit") limit: Int,
        @Query("fmt") format: String = "json"
    ): ReleaseSearchResponse
}