package com.tuapp.eleuthera.data.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Lee un archivo de cookies exportado del navegador: formato Netscape (cookies.txt) o JSON
 * (el que exportan extensiones como Cookie-Editor). yt-dlp solo entiende Netscape, así que el
 * JSON se convierte. Igual en Android y escritorio.
 */
object CookieText {

    /** Devuelve el texto en formato Netscape y cuántas cookies trae. Lanza si no vale. */
    fun normalize(raw: String): Pair<String, Int> {
        require(raw.length < 2_000_000) { "El archivo es demasiado grande para ser de cookies" }
        val first = raw.trimStart().firstOrNull()
        val text = if (first == '[' || first == '{') jsonToNetscape(raw) else raw
        // Cada cookie es una línea con 7 campos separados por tabuladores.
        val count = text.lineSequence()
            .map { it.removePrefix("#HttpOnly_") }
            .count { !it.startsWith("#") && it.split('\t').size >= 7 }
        require(count > 0) { "El archivo no contiene cookies en formato Netscape ni JSON" }
        return text to count
    }

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
                        domain.startsWith(".").tf(),
                        c.str("path") ?: "/",
                        c.flag("secure").tf(),
                        expires.toString(),
                        name,
                        c.str("value").orEmpty(),
                    ).joinToString("\t")
                )
            }
        }
    }
}
