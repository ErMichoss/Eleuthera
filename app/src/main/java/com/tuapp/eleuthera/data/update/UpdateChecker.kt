package com.tuapp.eleuthera.data.update

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Una versión publicada en GitHub más nueva que la instalada. */
data class AvailableUpdate(
    val version: String,
    val notes: String,
    /** Enlace directo al APK; si la release no trae APK, la página de la release. */
    val downloadUrl: String,
    val pageUrl: String,
)

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
)

/**
 * Comprueba si hay una release más nueva en GitHub. Usa la API pública (sin token), que
 * permite 60 peticiones por hora: por eso la comprobación automática va como mucho cada 12 h.
 */
@Singleton
class UpdateChecker @Inject constructor(@ApplicationContext private val ctx: Context) {

    private val prefs = ctx.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _available = MutableStateFlow<AvailableUpdate?>(null)
    /** Actualización pendiente de mostrar al usuario, o null. */
    val available: StateFlow<AvailableUpdate?> = _available.asStateFlow()

    val currentVersion: String = runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
    }.getOrNull().orEmpty()

    /** Comprobación al abrir la app: respeta el intervalo y las versiones omitidas. */
    suspend fun checkInBackground() {
        val last = prefs.getLong(KEY_LAST_CHECK, 0)
        if (System.currentTimeMillis() - last < INTERVAL_MS) return
        runCatching { fetchNewer() }
            .onSuccess { update ->
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                if (update != null && update.version != prefs.getString(KEY_SKIPPED, null)) {
                    _available.value = update
                }
            }
            .onFailure { Log.w(TAG, "No se pudo comprobar si hay actualizaciones", it) }
    }

    /** Comprobación pedida por el usuario: siempre consulta y muestra el resultado. */
    suspend fun checkNow(): Result<AvailableUpdate?> = runCatching { fetchNewer() }
        .onSuccess {
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            _available.value = it
        }

    /** "Más tarde": se oculta hasta la próxima comprobación. */
    fun dismiss() { _available.value = null }

    /** "Omitir esta versión": no se vuelve a avisar de ella automáticamente. */
    fun skip(version: String) {
        prefs.edit().putString(KEY_SKIPPED, version).apply()
        _available.value = null
    }

    private suspend fun fetchNewer(): AvailableUpdate? = withContext(Dispatchers.IO) {
        val conn = URL(LATEST_URL).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            // Sin releases publicadas, GitHub responde 404.
            if (conn.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return@withContext null
            check(conn.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${conn.responseCode}" }

            val release = json.decodeFromString<GithubRelease>(
                conn.inputStream.bufferedReader().use { it.readText() }
            )
            if (release.draft || release.prerelease) return@withContext null

            val version = release.tagName.removePrefix("v")
            if (!isNewer(version, currentVersion)) return@withContext null

            AvailableUpdate(
                version = version,
                notes = release.body.orEmpty().trim(),
                downloadUrl = release.assets.firstOrNull { it.name.endsWith(".apk") }?.url
                    ?: release.htmlUrl,
                pageUrl = release.htmlUrl,
            )
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "UpdateChecker"
        private const val LATEST_URL =
            "https://api.github.com/repos/ErMichoss/Eleuthera/releases/latest"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_SKIPPED = "skipped_version"
        private const val INTERVAL_MS = 12 * 60 * 60 * 1000L

        /** Compara "1.10.0" con "1.9.2" número a número. Si alguna no se entiende, no avisa. */
        internal fun isNewer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.split('.', '-').mapNotNull { it.toIntOrNull() }
            val a = parts(candidate)
            val b = parts(current)
            if (a.isEmpty() || b.isEmpty()) return false
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}