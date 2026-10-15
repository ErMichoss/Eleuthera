package com.tuapp.eleuthera.data.musicbrainz

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MusicBrainzApiService {

    @FormUrlEncoded
    @POST("oauth2/token")
    suspend fun exchangeToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String = "com.tuapp.eleuthera://oauth" // Obligatorio y exactamente igual
    ): MusicBrainzTokenResponse

    @Headers("User-Agent: MusicBrainzExplorerAndroid/1.0.0 (contacto@ejemplo.com)")
    @GET("ws/2/recording")
    suspend fun searchRecordings(
        @Query("query") query: String,
        @Query("limit") limit: Int,
        @Query("fmt") format: String = "json",
        @Query("inc") inc: String = "artist-rels+work-rels+tags+releases"
    ): RecordingSearchResponse

    @Headers("User-Agent: MusicBrainzExplorerAndroid/1.0.0 (contacto@ejemplo.com)")
    @GET("ws/2/release")
    suspend fun searchReleases(
        @Query("query") query: String,
        @Query("limit") limit: Int,
        @Query("fmt") format: String = "json"
    ): ReleaseSearchResponse

    @Headers("User-Agent: MusicBrainzExplorerAndroid/1.0.0 (contacto@ejemplo.com)")
    @GET("ws/2/recording/{id}")
    suspend fun getRecordingDetails(
        @Path("id") id: String,
        @Query("fmt") format: String = "json",
        @Query("inc") inc: String = "genres+user-tags+artist-rels+work-rels+releases"
    ): Recording
}