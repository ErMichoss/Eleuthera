package com.tuapp.eleuthera.data.library

import com.tuapp.eleuthera.data.db.PlaylistDao
import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.platform.FileUri
import com.tuapp.eleuthera.playback.Ffmpeg
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

/** Re-encodes desktop audio while preserving metadata, artwork, library identity, and playlists. */
class DesktopAudioFileProcessor(
    private val library: DesktopLibrary,
    private val playlistDao: PlaylistDao,
) {
    suspend fun existingDestinationName(track: Track): String? = withContext(Dispatchers.IO) {
        val source = sourceFile(track)
        val destination = destinationFile(source)
        destination.name.takeIf {
            destination.exists() && destination.canonicalFile != source.canonicalFile
        }
    }

    suspend fun process(
        track: Track,
        expectedDurationMs: Long,
        overwriteExisting: Boolean,
        audioArguments: List<String>,
    ): Track = withContext(Dispatchers.IO) {
        val source = sourceFile(track)
        check(source.isFile) { "El archivo de origen no existe." }
        val metadata = Ffmpeg.probe(source)
            ?: error("No se pudieron leer los metadatos del archivo de audio.")
        val ffmpeg = Ffmpeg.find() ?: error("No se encontró el motor de audio (ffmpeg).")
        val destination = destinationFile(source)
        val destinationExists =
            destination.exists() && destination.canonicalFile != source.canonicalFile
        check(!destinationExists || overwriteExisting) {
            "Ya existe otro archivo llamado ${destination.name}; confirma si quieres sobrescribirlo."
        }

        val tempDir = File(source.parentFile, ".audio_edit_${UUID.randomUUID()}").apply {
            check(mkdirs()) { "No se pudo preparar la carpeta temporal de edición." }
        }
        try {
            val output = File(tempDir, "edited.m4a")
            runInterruptible(Dispatchers.IO) {
                val command = buildList {
                    addAll(listOf(ffmpeg, "-hide_banner", "-loglevel", "error", "-y"))
                    addAll(listOf("-i", source.absolutePath))
                    addAll(audioArguments)
                    if (metadata.hasCover) {
                        addAll(
                            listOf(
                                "-map", "0:v:0",
                                "-c:v:0", "mjpeg",
                                "-disposition:v:0", "attached_pic",
                                "-metadata:s:v:0", "title=Album cover",
                                "-metadata:s:v:0", "comment=Cover (front)",
                            )
                        )
                    } else {
                        add("-vn")
                    }
                    addAll(listOf("-map_metadata", "0", "-map_metadata:s:a", "0:s:a:0"))
                    metadata.tags.forEach { (key, value) ->
                        addAll(listOf("-metadata", "$key=$value"))
                    }
                    addAll(listOf("-f", "ipod", "-c:a", "aac", "-b:a", "192k", output.absolutePath))
                }
                val process = ProcessBuilder(command).redirectErrorStream(true).start()
                val diagnostic = process.inputStream.bufferedReader().use { it.readText() }
                val exitCode = process.waitFor()
                check(exitCode == 0 && output.isFile && output.length() > 0) {
                    diagnostic.trim().takeIf { it.isNotEmpty() }
                        ?: "FFmpeg no pudo crear el archivo editado."
                }
            }

            val actualDurationMs = Ffmpeg.durationMs(output)
            check(kotlin.math.abs(actualDurationMs - expectedDurationMs) <= 1_500) {
                "El audio generado no coincide con la edición; el original se ha conservado."
            }
            val outputMetadata = Ffmpeg.probe(output)
                ?: error("No se pudieron validar los metadatos del audio generado.")
            metadata.tags.forEach { (key, value) ->
                check(outputMetadata.tags[key] == value) {
                    "El audio generado no conservó los metadatos; el original se ha conservado."
                }
            }
            if (metadata.hasCover) {
                check(outputMetadata.hasCover) {
                    "El audio generado perdió la portada; el original se ha conservado."
                }
            }

            val sourceBackup = File(tempDir, "original.audio")
            val destinationBackup = File(tempDir, "existing-destination.audio")
            val updatedTrackId = DesktopLibrary.idFor(destination.absolutePath)
            var sourceMoved = false
            var destinationMoved = false
            var outputMoved = false
            var playlistIdsUpdated = false
            try {
                if (destinationExists) {
                    Files.move(destination.toPath(), destinationBackup.toPath())
                    destinationMoved = true
                }
                Files.move(source.toPath(), sourceBackup.toPath())
                sourceMoved = true
                Files.move(output.toPath(), destination.toPath())
                outputMoved = true
                if (updatedTrackId != track.id) {
                    playlistDao.replaceTrackId(track.id, updatedTrackId)
                    playlistIdsUpdated = true
                }
            } catch (failure: Exception) {
                try {
                    if (playlistIdsUpdated) playlistDao.replaceTrackId(updatedTrackId, track.id)
                    if (outputMoved) Files.deleteIfExists(destination.toPath())
                    if (sourceMoved) {
                        Files.move(
                            sourceBackup.toPath(),
                            source.toPath(),
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                    }
                    if (destinationMoved) {
                        Files.move(
                            destinationBackup.toPath(),
                            destination.toPath(),
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                    }
                } catch (rollbackFailure: Exception) {
                    failure.addSuppressed(rollbackFailure)
                    throw IllegalStateException(
                        "Falló la edición y no se pudo restaurar completamente el archivo original.",
                        failure,
                    )
                }
                throw failure
            }

            library.refresh()
            track.copy(
                id = updatedTrackId,
                uri = FileUri(destination),
                durationMs = expectedDurationMs,
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun sourceFile(track: Track): File =
        (track.uri as? FileUri)?.file ?: error("No se pudo obtener el archivo de audio.")

    private fun destinationFile(source: File): File =
        File(source.parentFile, source.nameWithoutExtension + ".m4a")
}
