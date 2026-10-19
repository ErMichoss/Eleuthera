package com.tuapp.eleuthera.data.musicbrainz

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json

/**
 * Cliente de la API de MusicBrainz, igual en Android y escritorio. Antes era Retrofit + Gson;
 * ahora usa HttpURLConnection (está en los dos) y kotlinx-serialization, como el resto de la app.
 *
 * MusicBrainz pide un User-Agent que identifique la app y como mucho una petición por segundo
 * (las pausas las pone [com.tuapp.eleuthera.ui.musicbrainz.MusicBrainzController]).
 *
 * [clientId] y [clientSecret] solo hacen falta para iniciar sesión (OAuth). En Android salen
 * de BuildConfig (local.properties o secretos de GitHub); nunca se escriben en el código.
 */
class MusicBrainzService(
    private val clientId: String = "",
    private val clientSecret: String = "",
    appVersion: String = "1.0",
) {
    private val userAgent = "Eleuthera/$appVersion ( https://github.com/ErMichoss/Eleuthera )"
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private var accessToken: String? = null

    val canLogIn: Boolean get() = clientId.isNotBlank() && clientSecret.isNotBlank()

    /** Dirección de la página de MusicBrainz donde el usuario autoriza a la app. */
    fun authorizeUrl(scope: String = "tag rating"): String =
        "$BASE/oauth2/authorize?" + form(
            "client_id" to clientId,
            "response_type" to "code",
            "redirect_uri" to REDIRECT_URI,
            "scope" to scope,
        )

    /** Intercambia el código que devuelve MusicBrainz por el token y lo guarda. */
    suspend fun exchangeCodeForToken(code: String): Boolean = runCatching {
        val body = form(
            "client_id" to clientId,
            "client_secret" to clientSecret,
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to REDIRECT_URI,
        )
        accessToken = request("$BASE/oauth2/token", MusicBrainzTokenResponse.serializer(), postForm = body).accessToken
        true
    }.onFailure { it.printStackTrace() }.getOrDefault(false)

    suspend fun searchRecordings(query: String, limit: Int): RecordingSearchResponse =
        get("recording", RecordingSearchResponse.serializer(), "query" to query, "limit" to "$limit",
            "inc" to "artist-rels work-rels tags releases")

    suspend fun searchReleases(query: String, limit: Int): ReleaseSearchResponse =
        get("release", ReleaseSearchResponse.serializer(), "query" to query, "limit" to "$limit")

    suspend fun getRecordingDetails(id: String): Recording =
        get("recording/$id", Recording.serializer(), "inc" to "genres user-tags artist-rels work-rels releases")

    suspend fun getReleaseDetails(id: String): Release =
        get("release/$id", Release.serializer(), "inc" to "recordings media artist-credits")

    // ---- HTTP ----
    // Los "inc" van separados por espacios: en la URL se convierten en "+", que es lo que espera MusicBrainz.

    private suspend fun <T> get(path: String, strategy: DeserializationStrategy<T>, vararg params: Pair<String, String>): T =
        request("$BASE/ws/2/$path?" + form(*params, "fmt" to "json"), strategy)

    private suspend fun <T> request(url: String, strategy: DeserializationStrategy<T>, postForm: String? = null): T =
        withContext(Dispatchers.IO) {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("User-Agent", userAgent)
                conn.setRequestProperty("Accept", "application/json")
                accessToken?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
                if (postForm != null) {
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    conn.outputStream.use { it.write(postForm.toByteArray()) }
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    val detail = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty().take(300)
                    throw IOException(
                        when (code) {
                            503 -> "MusicBrainz está saturado; espera un momento y vuelve a intentarlo."
                            404 -> "MusicBrainz no tiene ese elemento."
                            else -> "MusicBrainz respondió $code. $detail".trim()
                        }
                    )
                }
                val text = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                json.decodeFromString(strategy, text)
            } finally {
                conn.disconnect()
            }
        }

    private fun form(vararg params: Pair<String, String>): String =
        params.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }

    companion object {
        private const val BASE = "https://musicbrainz.org"
        /** Tiene que ser exactamente la registrada en MusicBrainz para esta app. */
        const val REDIRECT_URI = "com.tuapp.eleuthera://oauth"
    }
}
