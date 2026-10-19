package com.tuapp.eleuthera.data.lrclib

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class LrclibService {

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
                .newBuilder()
                .header("User-Agent", "EleutheraApp/1.0.0")
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .build()

    private val api: LrclibApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LrclibApiService::class.java)
    }

    suspend fun searchLyrics(query: String): List<LrclibLyrics> =
        withContext(Dispatchers.IO) {
            api.searchLyrics(query)
        }

    suspend fun searchLyricsByMetadata(
        trackName: String,
        artistName: String,
        albumName: String? = null,
    ): List<LrclibLyrics> = withContext(Dispatchers.IO) {
        api.searchLyricsByMetadata(
            trackName = trackName,
            artistName = artistName,
            albumName = albumName,
        )
    }

    suspend fun getLyrics(
        trackName: String,
        artistName: String,
        albumName: String? = null,
        durationSeconds: Double? = null,
    ): LrclibLyrics = withContext(Dispatchers.IO) {
        api.getLyrics(
            trackName = trackName,
            artistName = artistName,
            albumName = albumName,
            durationSeconds = durationSeconds,
        )
    }

    suspend fun getLyricsById(id: Long): LrclibLyrics = withContext(Dispatchers.IO) {
        api.getLyricsById(id)
    }
}
