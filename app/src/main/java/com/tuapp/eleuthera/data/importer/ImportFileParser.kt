package com.tuapp.eleuthera.data.importer

import com.tuapp.eleuthera.domain.model.ImportedTrack

/**
 * Lee listas exportadas de otros servicios:
 *  - CSV de Exportify (Spotify), TuneMyMusic o Soundiiz (Spotify, Qobuz, Apple Music…),
 *    detectando las columnas por su nombre.
 *  - Texto plano con una canción por línea, "Artista - Título".
 */
object ImportFileParser {

    private val titleHeaders = listOf("track name", "title", "song", "song name", "track", "name")
    private val artistHeaders = listOf("artist name(s)", "artist name", "artist names", "artists", "artist")
    private val durationHeaders = listOf("duration (ms)", "track duration (ms)", "duration_ms", "duration")
    private val playlistHeaders = listOf("playlist name", "playlist")

    fun parse(text: String): List<ImportedTrack> {
        val clean = text.removePrefix("﻿").trim()
        if (clean.isEmpty()) return emptyList()

        val firstLine = clean.lineSequence().first()
        val delimiter = listOf(',', ';', '\t').maxBy { d -> firstLine.count { it == d } }
        val rows = parseCsv(clean, delimiter)
        val header = rows.firstOrNull()?.map { it.trim().lowercase() }.orEmpty()

        val titleCol = header.findColumn(titleHeaders)
        // Sin columna de título reconocible: se trata como texto plano.
        if (titleCol < 0) return parsePlainText(clean)

        val artistCol = header.findColumn(artistHeaders)
        val durationCol = header.findColumn(durationHeaders)
        val playlistCol = header.findColumn(playlistHeaders)
        val durationInMs = durationCol >= 0 && "ms" in header[durationCol]

        return rows.drop(1).mapNotNull { row ->
            val title = row.getOrNull(titleCol)?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            ImportedTrack(
                title = title,
                artist = row.getOrNull(artistCol)?.let(::cleanArtists).orEmpty(),
                durationSec = row.getOrNull(durationCol)?.let { parseDuration(it, durationInMs) },
                playlist = row.getOrNull(playlistCol)?.trim()?.ifEmpty { null },
            )
        }
    }

    /** Busca primero coincidencia exacta y luego parcial, evitando columnas de álbum. */
    private fun List<String>.findColumn(candidates: List<String>): Int {
        for (c in candidates) indexOf(c).let { if (it >= 0) return it }
        for (c in candidates) {
            indexOfFirst { c in it && "album" !in it && "uri" !in it && "id" != it }
                .let { if (it >= 0) return it }
        }
        return -1
    }

    private fun parsePlainText(text: String): List<ImportedTrack> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val sep = line.indexOf(" - ")
                if (sep > 0) ImportedTrack(title = line.substring(sep + 3).trim(), artist = line.substring(0, sep).trim())
                else ImportedTrack(title = line)
            }
            .toList()

    /** "Artista A, Artista B" o "A;B" -> "Artista A, Artista B". */
    private fun cleanArtists(raw: String): String =
        raw.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")

    private fun parseDuration(raw: String, inMs: Boolean): Long? {
        val v = raw.trim()
        if (':' in v) {
            val parts = v.split(':').mapNotNull { it.toLongOrNull() }
            return parts.fold(0L) { acc, p -> acc * 60 + p }.takeIf { it > 0 }
        }
        val n = v.toDoubleOrNull() ?: return null
        val seconds = if (inMs || n > 10_000) n / 1000 else n
        return seconds.toLong().takeIf { it > 0 }
    }

    /** CSV estándar: campos entre comillas, comillas dobles escapadas y saltos de línea dentro. */
    private fun parseCsv(text: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == delimiter -> { row.add(field.toString()); field.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row.add(field.toString()); field.clear()
                    if (row.any { it.isNotBlank() }) rows.add(row)
                    row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        row.add(field.toString())
        if (row.any { it.isNotBlank() }) rows.add(row)
        return rows
    }
}