package com.tuapp.eleuthera.data.update

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tuapp.eleuthera.data.settings.PreferenceStores
import com.tuapp.eleuthera.platform.DesktopPaths
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Una versión de escritorio publicada en GitHub más nueva que la instalada. */
data class DesktopUpdate(
    val version: String,
    val notes: String,
    /** Instalador para este sistema (.msi, .deb o .dmg). */
    val downloadUrl: String,
    val pageUrl: String,
)

@Serializable
private data class Release(
    @SerialName("tag_name") val tagName: String,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<Asset> = emptyList(),
)

@Serializable
private data class Asset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
)

/**
 * Lo mismo que UpdateChecker en Android, para escritorio: mira la última release de GitHub
 * (como mucho cada 12 h) y avisa si trae un instalador para este sistema más nuevo que el
 * instalado. Si la release solo trae el APK, no avisa: no hay nada que instalar aquí.
 */
class DesktopUpdateChecker(stores: PreferenceStores, val currentVersion: String) {

    private val store = stores.get("updates")
    private val lastCheckKey = longPreferencesKey("last_check")
    private val skippedKey = stringPreferencesKey("skipped_version")
    private val json = Json { ignoreUnknownKeys = true }

    private val _available = MutableStateFlow<DesktopUpdate?>(null)
    /** Actualización pendiente de mostrar al usuario, o null. */
    val available: StateFlow<DesktopUpdate?> = _available.asStateFlow()

    private val http by lazy {
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .proxy(ProxySelector.getDefault())
            .build()
    }

    /** Comprobación al abrir la app: respeta el intervalo y las versiones omitidas. */
    suspend fun checkInBackground() {
        val prefs = store.data.first()
        if (System.currentTimeMillis() - (prefs[lastCheckKey] ?: 0L) < INTERVAL_MS) return
        runCatching { fetchNewer() }.onSuccess { update ->
            store.edit { it[lastCheckKey] = System.currentTimeMillis() }
            if (update != null && update.version != prefs[skippedKey]) _available.value = update
        }
    }

    /** Comprobación pedida por el usuario: siempre consulta y muestra el resultado. */
    suspend fun checkNow(): Result<DesktopUpdate?> = runCatching { fetchNewer() }.onSuccess {
        store.edit { p -> p[lastCheckKey] = System.currentTimeMillis() }
        _available.value = it
    }

    /** "Más tarde": se oculta hasta la próxima comprobación. */
    fun dismiss() { _available.value = null }

    /** "Omitir esta versión": no se vuelve a avisar de ella automáticamente. */
    suspend fun skip(version: String) {
        store.edit { it[skippedKey] = version }
        _available.value = null
    }

    private suspend fun fetchNewer(): DesktopUpdate? = withContext(Dispatchers.IO) {
        val request = HttpRequest.newBuilder(URI(LATEST_URL))
            .header("Accept", "application/vnd.github+json")
            .timeout(Duration.ofSeconds(15))
            .GET().build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        // Sin releases publicadas, GitHub responde 404.
        if (response.statusCode() == 404) return@withContext null
        check(response.statusCode() == 200) { "HTTP ${response.statusCode()}" }

        val release = json.decodeFromString(Release.serializer(), response.body())
        if (release.draft || release.prerelease) return@withContext null
        val version = release.tagName.removePrefix("v")
        if (!isNewer(version, currentVersion)) return@withContext null

        val ext = when {
            DesktopPaths.isWindows -> ".msi"
            DesktopPaths.isMac -> ".dmg"
            else -> ".deb"
        }
        val candidates = release.assets.filter { it.name.endsWith(ext, ignoreCase = true) }
        // En macOS hay dos .dmg (…-mac-arm64.dmg y …-mac-x64.dmg): el de este procesador.
        val arch = if (System.getProperty("os.arch").orEmpty().contains("aarch64")) "arm64" else "x64"
        val installer = candidates.firstOrNull { it.name.contains("-$arch", ignoreCase = true) }
            ?: candidates.firstOrNull()
            ?: return@withContext null
        DesktopUpdate(version, release.body.orEmpty().trim(), installer.url, release.htmlUrl)
    }

    companion object {
        private const val LATEST_URL = "https://api.github.com/repos/ErMichoss/Eleuthera/releases/latest"
        private const val INTERVAL_MS = 12 * 60 * 60 * 1000L

        /** Compara "1.10.0" con "1.9.2" número a número. Si alguna no se entiende, no avisa. */
        fun isNewer(candidate: String, current: String): Boolean {
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
