package com.tuapp.eleuthera.data.lrclib

import java.io.IOException
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class LrclibLyricsNotFoundException : IOException("LRCLIB no encontró una letra que coincida.")

@Serializable
private data class LyricsCacheEntry(
    val key: String,
    val cachedAtMillis: Long,
    val lyrics: List<LrclibLyrics>,
)

@Serializable
private data class LyricsCacheFile(
    @SerialName("entries") val entries: List<LyricsCacheEntry> = emptyList(),
)

/**
 * LRCLIB client shared by Android and desktop. HTTP failures are surfaced to callers.
 */
class LrclibService(
    private val cacheFile: File,
    appVersion: String = "1.0",
) {

    private val userAgent = "Eleuthera/$appVersion (https://github.com/ErMichoss/Eleuthera)"
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val cacheMutex = Mutex()
    private var cacheLoaded = false
    private var lastMetadataRequestAtMillis = 0L
    private val cache = linkedMapOf<String, LyricsCacheEntry>()

    suspend fun searchLyrics(query: String): List<LrclibLyrics> =
        get("search", ListSerializer(LrclibLyrics.serializer()), "q" to query)

    suspend fun searchLyricsByMetadata(
        trackName: String,
        artistName: String,
        albumName: String? = null,
        refreshCache: Boolean = false,
    ): List<LrclibLyrics> {
        val key = listOf(trackName, artistName, albumName.orEmpty())
            .joinToString("|") { it.trim().lowercase() }
        return cacheMutex.withLock {
            withContext(Dispatchers.IO) {
                loadCacheIfNeeded()
                val now = System.currentTimeMillis()
                if (!refreshCache) cache[key]?.let { entry ->
                    val lifetime = if (entry.lyrics.isEmpty()) NEGATIVE_CACHE_TTL_MS else CACHE_TTL_MS
                    if (now - entry.cachedAtMillis < lifetime) return@withContext entry.lyrics
                    cache.remove(key)
                }

                val waitMs = MIN_METADATA_REQUEST_INTERVAL_MS -
                    (System.currentTimeMillis() - lastMetadataRequestAtMillis)
                if (waitMs > 0) delay(waitMs)
                lastMetadataRequestAtMillis = System.currentTimeMillis()
                val lyrics = get(
                    "search",
                    ListSerializer(LrclibLyrics.serializer()),
                    *buildList {
                        add("track_name" to trackName)
                        add("artist_name" to artistName)
                        albumName?.let { add("album_name" to it) }
                    }.toTypedArray(),
                )
                cache[key] = LyricsCacheEntry(key, now, lyrics)
                trimCache(now)
                persistCache()
                lyrics
            }
        }
    }

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
                if (code == 404) throw LrclibLyricsNotFoundException()
                throw IOException(
                    when (code) {
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

    private fun loadCacheIfNeeded() {
        if (cacheLoaded) return
        if (cacheFile.isFile) {
            try {
                json.decodeFromString<LyricsCacheFile>(cacheFile.readText())
                    .entries
                    .forEach { cache[it.key] = it }
            } catch (e: SerializationException) {
                System.err.println("Ignoring invalid LRCLIB cache at ${cacheFile.path}: ${e.message}")
                if (!cacheFile.delete()) {
                    throw IOException("No se pudo descartar la caché LRCLIB dañada.", e)
                }
            }
        }
        cacheLoaded = true
    }

    private fun trimCache(now: Long) {
        cache.entries.removeAll { (_, entry) ->
            val lifetime = if (entry.lyrics.isEmpty()) NEGATIVE_CACHE_TTL_MS else CACHE_TTL_MS
            now - entry.cachedAtMillis >= lifetime
        }
        while (cache.size > MAX_CACHE_ENTRIES) {
            cache.remove(cache.keys.first())
        }
    }

    private fun persistCache() {
        cacheFile.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) {
                throw IOException("No se pudo crear la carpeta de caché LRCLIB.")
            }
        }
        val temporaryFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
        try {
            temporaryFile.writeText(json.encodeToString(LyricsCacheFile.serializer(), LyricsCacheFile(cache.values.toList())))
            Files.move(
                temporaryFile.toPath(),
                cacheFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } finally {
            if (temporaryFile.exists() && !temporaryFile.delete()) {
                System.err.println("Could not remove temporary LRCLIB cache ${temporaryFile.path}")
            }
        }
    }

    private companion object {
        const val BASE = "https://lrclib.net"
        const val MAX_CACHE_ENTRIES = 2_000
        const val MIN_METADATA_REQUEST_INTERVAL_MS = 1_000L
        const val CACHE_TTL_MS = 180L * 24 * 60 * 60 * 1_000
        const val NEGATIVE_CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1_000
    }
}
