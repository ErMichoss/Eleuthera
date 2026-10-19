package com.tuapp.eleuthera.data.settings

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Cookies de YouTube en escritorio: el archivo exportado del navegador se guarda (ya en
 * formato Netscape) en la carpeta de datos de la app, y yt-dlp recibe una copia en cada uso.
 */
class DesktopCookieStore(dataDir: File) {

    private val file = File(dataDir, "cookies.txt")

    private val _present = MutableStateFlow(file.exists())
    /** true si hay cookies cargadas. */
    val present: StateFlow<Boolean> = _present.asStateFlow()

    /** Importa un cookies.txt o .json. Devuelve cuántas cookies contiene. */
    suspend fun import(source: File): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val (text, count) = CookieText.normalize(source.readText())
            file.parentFile?.mkdirs()
            file.writeText(text)
            _present.value = true
            count
        }
    }

    /**
     * Trae la sesión de YouTube del navegador [browser] ("firefox", "chrome", "edge"...): yt-dlp
     * lee sus cookies una vez y aquí se guardan solo las de YouTube y Google. Devuelve cuántas
     * son; falla si no se pudieron leer o si no hay sesión iniciada.
     *
     * [export] es quien llama a yt-dlp (DesktopYtDlp.exportBrowserCookies) y deja en el archivo
     * que recibe todas las cookies del navegador.
     */
    suspend fun importFromBrowser(browser: String, export: suspend (String, File) -> Unit): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val tmp = File.createTempFile("eleuthera-browser-", ".txt")
                try {
                    export(browser, tmp)
                    // Solo YouTube y Google: el resto de cookies del navegador no son asunto nuestro.
                    val lines = tmp.readLines().filter { line ->
                        // Líneas de cookie (las de comentario empiezan por "#", salvo #HttpOnly_).
                        line.isNotBlank() && (!line.startsWith("#") || line.startsWith("#HttpOnly_"))
                    }.filter { line ->
                        val domain = line.removePrefix("#HttpOnly_").substringBefore('\t').trimStart('.')
                        domain.endsWith("youtube.com") || domain.endsWith("google.com")
                    }
                    require(lines.isNotEmpty()) { "No hay cookies de YouTube en ese navegador." }
                    val names = lines.mapNotNull { it.split('\t').getOrNull(5) }.toSet()
                    require(names.any { it in LOGIN_COOKIES }) {
                        "En ese navegador no hay una sesión de YouTube iniciada."
                    }
                    val text = "# Netscape HTTP Cookie File\n" + lines.joinToString("\n") + "\n"
                    file.parentFile?.mkdirs()
                    file.writeText(text)
                    _present.value = true
                    lines.size
                } finally {
                    tmp.delete()
                }
            }
        }

    fun clear() {
        file.delete()
        _present.value = false
    }

    /**
     * Copia las cookies a [dir] y devuelve la copia, o null si no hay. Cada proceso de yt-dlp
     * usa su propia copia porque yt-dlp reescribe el archivo y hay varias descargas a la vez.
     */
    fun snapshotInto(dir: File): File? =
        if (file.exists()) file.copyTo(File(dir, "cookies.txt"), overwrite = true) else null

    private companion object {
        /** Cookies que solo existen con una cuenta iniciada. */
        val LOGIN_COOKIES = setOf("SAPISID", "__Secure-3PAPISID", "LOGIN_INFO", "SID", "__Secure-1PSID")
    }
}
