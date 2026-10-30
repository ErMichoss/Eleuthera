package com.tuapp.eleuthera.data.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.yausername.ffmpeg.FFmpeg
import com.tuapp.eleuthera.data.metadata.MetaDataManager
import com.tuapp.eleuthera.data.metadata.TrackMetadata
import com.tuapp.eleuthera.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

@Singleton
class AudioTrimmer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val library: MediaLibrary,
    private val metadataManager: MetaDataManager,
) {
    suspend fun durationMs(uri: Uri): Long = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(0) ?: 0
        } finally {
            retriever.release()
        }
    }

    /**
     * Genera un M4A con el intervalo seleccionado o, en modo eliminación, con el resto del audio.
     * Verifica la duración resultante antes de sustituir el archivo original.
     */
    suspend fun trim(
        track: Track,
        startMs: Long,
        endMs: Long,
        durationMs: Long,
        removeSelectedSegment: Boolean,
    ) =
        withContext(Dispatchers.IO) {
            require(startMs >= 0 && endMs > startMs) { "El tramo seleccionado no es válido." }
            require(durationMs > 0 && endMs <= durationMs) {
                "El tramo seleccionado supera la duración del archivo."
            }
            if (removeSelectedSegment) {
                require(startMs > 0 || endMs < durationMs) {
                    "No se puede quitar toda la canción."
                }
            }
            val directory = File(context.cacheDir, "trim/${UUID.randomUUID()}").apply {
                check(mkdirs()) { "No se pudo preparar el recorte." }
            }
            try {
                val input = File(directory, "source.audio")
                context.contentResolver.openInputStream(track.uri)?.use { source ->
                    input.outputStream().use { destination -> source.copyTo(destination) }
                } ?: error("No se pudo abrir el archivo de audio.")

                val originalName = library.displayName(track.uri)
                    ?: error("No se pudo determinar el nombre del archivo original.")
                val metadata = metadataManager.getTrackMetadata(track)
                val artworkFile = metadata.coverArtwork?.takeIf { it.isNotEmpty() }?.let { artwork ->
                    File(directory, if (artwork.isPng()) "cover.png" else "cover.jpg")
                        .apply { writeBytes(artwork) }
                }
                val output = File(directory, "trimmed.m4a")
                FFmpeg.getInstance().init(context)
                val ffmpeg = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
                check(ffmpeg.isFile) { "No se encontró el motor de audio." }
                val libraryPath = File(
                    context.noBackupFilesDir,
                    "youtubedl-android/packages/ffmpeg/usr/lib",
                )
                check(libraryPath.isDirectory) { "No se pudieron cargar las bibliotecas de audio." }

                runInterruptible(Dispatchers.IO) {
                    val command = buildList {
                        addAll(listOf(ffmpeg.absolutePath, "-hide_banner", "-loglevel", "error", "-y"))
                        addAll(listOf("-i", input.absolutePath))
                        artworkFile?.let { addAll(listOf("-i", it.absolutePath)) }
                        if (removeSelectedSegment) {
                            addAll(removalFilter(startMs, endMs, durationMs))
                        } else {
                            val seconds = (endMs - startMs) / 1000.0
                            addAll(
                                listOf(
                                    "-ss", String.format(Locale.US, "%.3f", startMs / 1000.0),
                                    "-t", String.format(Locale.US, "%.3f", seconds),
                                    "-map", "0:a:0",
                                )
                            )
                        }
                        if (artworkFile != null) {
                            addAll(
                                listOf(
                                    "-map", "1:v:0",
                                    "-c:v:0", "mjpeg",
                                    "-disposition:v:0", "attached_pic",
                                    "-metadata:s:v:0", "title=Album cover",
                                    "-metadata:s:v:0", "comment=Cover (front)",
                                )
                            )
                        } else {
                            add("-vn")
                        }
                        addAll(
                            listOf(
                                "-map_metadata", "0",
                                "-metadata", "title=${metadata.title}",
                                "-metadata", "artist=${metadata.artist}",
                                "-metadata", "album=${metadata.album}",
                                "-f", "ipod", "-c:a", "aac",
                                "-b:a", "192k",
                            )
                        )
                        add(output.absolutePath)
                    }
                    val nativeLibraryPath = context.applicationInfo.nativeLibraryDir
                    val process = ProcessBuilder(command)
                        .redirectErrorStream(true).apply {
                        environment()["LD_LIBRARY_PATH"] =
                            "$nativeLibraryPath:${libraryPath.absolutePath}"
                        environment()["PATH"] =
                            "${environment()["PATH"].orEmpty()}:$nativeLibraryPath"
                        environment()["TMPDIR"] = context.cacheDir.absolutePath
                    }.start()
                    try {
                        val diagnostic = process.inputStream.bufferedReader().use { it.readText() }
                        val exitCode = process.waitFor()
                        check(exitCode == 0 && output.isFile && output.length() > 0) {
                            diagnostic.trim().takeIf { it.isNotEmpty() }
                                ?: "FFmpeg no pudo crear el archivo recortado."
                        }
                    } finally {
                        if (process.isAlive) process.destroyForcibly()
                    }
                }

                val outputName = originalName.substringBeforeLast('.') + ".m4a"
                val updatedDurationMs = if (removeSelectedSegment) {
                    durationMs - (endMs - startMs)
                } else {
                    endMs - startMs
                }
                val actualOutputDurationMs = readDurationMs(output)
                check(kotlin.math.abs(actualOutputDurationMs - updatedDurationMs) <= 1_500) {
                    "El audio generado no coincide con la operación seleccionada; el original se ha conservado."
                }
                verifyMetadata(output, metadata)
                library.replaceAudio(track, output, input, outputName)
                    .copy(durationMs = updatedDurationMs)
            } finally {
                directory.deleteRecursively()
            }
        }

    /** Comprueba el archivo generado antes de sustituir el original. */
    private fun readDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(0)
                ?: error("No se pudo validar la duración del audio generado.")
        } finally {
            retriever.release()
        }
    }

    /** No sustituye el original si el contenedor generado perdió las etiquetas o la portada. */
    private fun verifyMetadata(file: File, expected: TrackMetadata) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val checks = listOf(
                MediaMetadataRetriever.METADATA_KEY_TITLE to expected.title,
                MediaMetadataRetriever.METADATA_KEY_ARTIST to expected.artist,
                MediaMetadataRetriever.METADATA_KEY_ALBUM to expected.album,
            )
            checks.forEach { (key, value) ->
                if (value.isNotBlank()) {
                    check(retriever.extractMetadata(key)?.trim() == value.trim()) {
                        "El audio generado no conservó todos los metadatos; el original se ha conservado."
                    }
                }
            }
            if (expected.coverArtwork?.isNotEmpty() == true) {
                check(retriever.embeddedPicture?.isNotEmpty() == true) {
                    "El audio generado perdió la portada; el original se ha conservado."
                }
            }
        } finally {
            retriever.release()
        }
    }

    /** Une el audio anterior y posterior al intervalo, con casos especiales en los extremos. */
    private fun removalFilter(startMs: Long, endMs: Long, durationMs: Long): List<String> {
        fun seconds(ms: Long) = String.format(Locale.US, "%.3f", ms / 1000.0)

        val filter = when {
            startMs == 0L ->
                "[0:a:0]atrim=start=${seconds(endMs)},asetpts=PTS-STARTPTS[outa]"
            endMs == durationMs ->
                "[0:a:0]atrim=end=${seconds(startMs)},asetpts=PTS-STARTPTS[outa]"
            else -> buildString {
                append("[0:a:0]atrim=end=${seconds(startMs)},asetpts=PTS-STARTPTS[before];")
                append("[0:a:0]atrim=start=${seconds(endMs)},asetpts=PTS-STARTPTS[after];")
                append("[before][after]concat=n=2:v=0:a=1[outa]")
            }
        }
        return listOf("-filter_complex", filter, "-map", "[outa]")
    }

    private fun ByteArray.isPng(): Boolean =
        size >= PNG_SIGNATURE.size && PNG_SIGNATURE.indices.all { this[it] == PNG_SIGNATURE[it] }

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
    }
}
