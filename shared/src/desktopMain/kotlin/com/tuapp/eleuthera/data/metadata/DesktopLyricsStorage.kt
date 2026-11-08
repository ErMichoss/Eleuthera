package com.tuapp.eleuthera.data.metadata

import com.tuapp.eleuthera.platform.FileUri
import com.tuapp.eleuthera.ui.lyrics.LyricsStorage
import com.tuapp.eleuthera.ui.lyrics.LyricsTrack
import com.tuapp.eleuthera.ui.lyrics.LocalLyricsResult
import com.tuapp.eleuthera.playback.Ffmpeg
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads local subtitle sidecars and embedded lyrics, and saves edits beside desktop audio. */
class DesktopLyricsStorage : LyricsStorage {
    override suspend fun read(track: LyricsTrack): LocalLyricsResult = withContext(Dispatchers.IO) {
        val source = fileFor(track)
        val base = source.nameWithoutExtension
        val sidecar = sequenceOf(File(source.parentFile, "$base.lrc"), File(source.parentFile, "$base.srt"))
            .firstOrNull { it.isFile && it.length() > 0 }
        if (sidecar != null) {
            return@withContext LocalLyricsResult(text = sidecar.readText(Charsets.UTF_8))
        }

        val probe = Ffmpeg.probe(source)
            ?: return@withContext LocalLyricsResult(sidecarReadError = IllegalStateException("No se pudieron leer los metadatos del audio."))
        val embeddedLyrics = probe.tag("syncedlyrics", "lyrics", "unsyncedlyrics")
        LocalLyricsResult(text = embeddedLyrics)
    }

    override suspend fun save(
        track: LyricsTrack,
        lyrics: String,
        isStillCurrent: () -> Boolean,
        replaceExisting: Boolean,
    ): String = withContext(Dispatchers.IO) {
        check(isStillCurrent()) { "La canción cambió antes de guardar sus letras." }
        val source = fileFor(track)
        val srt = SRT_TIMESTAMP.containsMatchIn(lyrics) &&
            !LRC_TIMESTAMP.containsMatchIn(lyrics)
        val extension = if (srt) "srt" else "lrc"
        val destination = File(source.parentFile, "${source.nameWithoutExtension}.$extension")
        check(replaceExisting || !destination.exists()) {
            "Ya existe ${destination.name}; no se han modificado las letras."
        }
        val temporary = File(
            destination.parentFile,
            ".${destination.name}.${UUID.randomUUID()}.tmp",
        )
        try {
            temporary.writeText(lyrics.trim() + "\n", Charsets.UTF_8)
            check(isStillCurrent()) { "La canción cambió antes de guardar sus letras." }
            val options = if (replaceExisting) {
                arrayOf(
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } else {
                arrayOf(StandardCopyOption.ATOMIC_MOVE)
            }
            try {
                Files.move(temporary.toPath(), destination.toPath(), *options)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                val fallback = if (replaceExisting) {
                    arrayOf(StandardCopyOption.REPLACE_EXISTING)
                } else {
                    emptyArray()
                }
                Files.move(temporary.toPath(), destination.toPath(), *fallback)
            }
        } finally {
            Files.deleteIfExists(temporary.toPath())
        }
        "Letra actualizada junto al archivo: .$extension."
    }

    private fun fileFor(track: LyricsTrack): File {
        val uri = runCatching { java.net.URI(track.uri) }
            .getOrElse { throw IllegalArgumentException("La ubicación del audio no es válida.", it) }
        return (FileUri(uri).file).also {
            require(it.isFile) { "No se encontró el archivo de audio." }
        }
    }

    private companion object {
        val LRC_TIMESTAMP = Regex("""\[\d{1,3}:[0-5]\d(?:[.:]\d{1,3})?]""")
        val SRT_TIMESTAMP = Regex("""\d{2}:\d{2}:\d{2}[,.]\d{3}\s+-->""")
    }
}
