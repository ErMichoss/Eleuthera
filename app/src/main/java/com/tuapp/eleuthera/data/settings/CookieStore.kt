package com.tuapp.eleuthera.data.settings

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Guarda el cookies.txt del usuario en el almacenamiento privado de la app: ninguna otra app
 * puede leerlo y se borra al desinstalar. Equivale al `--cookies cookies.txt` del script.
 */
@Singleton
class CookieStore @Inject constructor(@ApplicationContext private val ctx: Context) {

    private val file = File(ctx.filesDir, "cookies.txt")

    private val _present = MutableStateFlow(file.exists())
    /** true si hay cookies cargadas. */
    val present: StateFlow<Boolean> = _present.asStateFlow()

    /**
     * Importa cookies en formato Netscape (cookies.txt) o JSON (el que exportan extensiones
     * como EditThisCookie o Cookie-Editor). Devuelve cuántas cookies contiene.
     */
    suspend fun import(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = ctx.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() }
                ?: error("No se pudo leer el archivo")
            require(raw.length < 2_000_000) { "El archivo es demasiado grande para ser de cookies" }

            // yt-dlp solo entiende Netscape: el JSON se convierte antes de guardarlo.
            val first = raw.trimStart().firstOrNull()
            val text = if (first == '[' || first == '{') jsonToNetscape(raw) else raw

            // Cada cookie es una línea con 7 campos separados por tabuladores.
            val count = text.lineSequence()
                .map { it.removePrefix("#HttpOnly_") }
                .count { !it.startsWith("#") && it.split('\t').size >= 7 }
            require(count > 0) { "El archivo no contiene cookies en formato Netscape ni JSON" }

            file.writeText(text)
            _present.value = true
            count
        }
    }

    /** Convierte la exportación JSON de una extensión de navegador a formato Netscape. */
    private fun jsonToNetscape(raw: String): String {
        val root = runCatching { Json.parseToJsonElement(raw) }
            .getOrElse { error("El archivo JSON no es válido") }
        // Lo habitual es una lista; algunas extensiones la envuelven en {"cookies": [...]}.
        val items = (root as? JsonArray)
            ?: ((root as? JsonObject)?.get("cookies") as? JsonArray)
            ?: error("El JSON no contiene una lista de cookies")

        fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
        fun JsonObject.flag(key: String) = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false
        fun Boolean.tf() = if (this) "TRUE" else "FALSE"

        return buildString {
            appendLine("# Netscape HTTP Cookie File")
            for (item in items) {
                val c = item as? JsonObject ?: continue
                val domain = c.str("domain") ?: continue
                val name = c.str("name") ?: continue
                val expires = (c["expirationDate"] as? JsonPrimitive)?.doubleOrNull?.toLong() ?: 0L
                if (c.flag("httpOnly")) append("#HttpOnly_")
                appendLine(
                    listOf(
                        domain,
                        domain.startsWith(".").tf(),      // válida también para subdominios
                        c.str("path") ?: "/",
                        c.flag("secure").tf(),
                        expires.toString(),               // 0 = cookie de sesión
                        name,
                        c.str("value").orEmpty(),
                    ).joinToString("\t")
                )
            }
        }
    }

    /**
     * Guarda las cookies capturadas en el inicio de sesión dentro de la app. [header] es el
     * texto "nombre=valor; nombre2=valor2" que devuelve CookieManager para youtube.com.
     */
    fun saveFromHeader(header: String): Result<Int> = runCatching {
        val pairs = header.split(";")
            .map { it.trim() }
            .filter { '=' in it }
            .map { it.substringBefore('=') to it.substringAfter('=') }
        require(pairs.any { (name, _) -> name in LOGIN_COOKIES }) {
            "No se detecta la sesión iniciada. Entra en tu cuenta antes de guardar."
        }
        // CookieManager no da caducidad: se fija a 6 meses, como haría una sesión normal.
        val expires = System.currentTimeMillis() / 1000 + 180L * 24 * 3600
        val text = buildString {
            appendLine("# Netscape HTTP Cookie File")
            pairs.forEach { (name, value) ->
                appendLine(listOf(".youtube.com", "TRUE", "/", "TRUE", "$expires", name, value)
                    .joinToString("\t"))
            }
        }
        file.writeText(text)
        _present.value = true
        pairs.size
    }

    fun clear() {
        file.delete()
        _present.value = false
    }

    /**
     * Copia las cookies a [dir] y devuelve la copia, o null si no hay. Cada proceso de yt-dlp
     * usa su propia copia porque yt-dlp reescribe el archivo y hay varias descargas a la vez.
     */
    private companion object {
        /** Cookies que solo existen con una cuenta iniciada. */
        val LOGIN_COOKIES = setOf("SAPISID", "__Secure-3PAPISID", "LOGIN_INFO", "SID", "__Secure-1PSID")
    }

    fun snapshotInto(dir: File): File? =
        if (file.exists()) file.copyTo(File(dir, "cookies.txt"), overwrite = true) else null
}
