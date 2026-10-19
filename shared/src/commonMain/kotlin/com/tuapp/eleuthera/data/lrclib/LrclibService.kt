package com.tuapp.eleuthera.data.lrclib

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * LRCLIB client shared by Android and desktop. HTTP failures are surfaced to callers.
 */
class LrclibService(appVersion: String = "1.0") {

    private val userAgent = "Eleuthera/$appVersion (https://github.com/ErMichoss/Eleuthera)"
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    suspend fun searchLyrics(query: String): List<LrclibLyrics> =
        get("search", ListSerializer(LrclibLyrics.serializer()), "q" to query)

    suspend fun searchLyricsByMetadata(
        trackName: String,
        artistName: String,
        albumName: String? = null,
    ): List<LrclibLyrics> = get(
        "search",
        ListSerializer(LrclibLyrics.serializer()),
        *buildList {
            add("track_name" to trackName)
            add("artist_name" to artistName)
            albumName?.let { add("album_name" to it) }
        }.toTypedArray(),
    )

    suspend fun getLyrics(
        trackName: String,
        artistName: String,
        albumName: String? = null,
        durationSeconds: Double? = null,
    ): LrclibLyrics = get(
        "get",
        LrclibLyrics.serializer(),
        *buildList {
            add("track_name" to trackName)
            add("artist_name" to artistName)
            albumName?.let { add("album_name" to it) }
            durationSeconds?.let { add("duration" to it.toString()) }
        }.toTypedArray(),
    )

    suspend fun getLyricsById(id: Long): LrclibLyrics =
        get("get/$id", LrclibLyrics.serializer())

    private suspend fun <T> get(
        path: String,
        strategy: DeserializationStrategy<T>,
        vararg params: Pair<String, String>,
    ): T = withContext(Dispatchers.IO) {
        val query = params.takeIf { it.isNotEmpty() }?.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val url = "$BASE/api/$path" + (query?.let { "?$it" } ?: "")
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()
                    ?.use { it.readText() }.orEmpty().take(300)
                throw IOException(
                    when (code) {
                        404 -> "LRCLIB no encontró una letra que coincida."
                        429 -> "LRCLIB está limitando las peticiones; espera un momento y vuelve a intentarlo."
                        else -> "LRCLIB respondió $code. $detail".trim()
                    }
                )
            }
            val body = connection.inputStream.bufferedReader(StandardCharsets.UTF_8)
                .use { it.readText() }
            json.decodeFromString(strategy, body)
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val BASE = "https://lrclib.net"
    }
}
