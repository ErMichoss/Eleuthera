package com.tuapp.eleuthera.data.library

import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.platform.FileUri
import com.tuapp.eleuthera.platform.PlatformUri
import com.tuapp.eleuthera.playback.Ffmpeg
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Desktop implementation of the shared audio-trimming contract. */
class DesktopAudioTrimmer(
    private val processor: DesktopAudioFileProcessor,
) : AudioTrimmer {
    override suspend fun durationMs(uri: PlatformUri): Long = withContext(Dispatchers.IO) {
        val file = (uri as? FileUri)?.file ?: return@withContext 0L
        Ffmpeg.durationMs(file)
    }

    override suspend fun existingDestinationName(track: Track): String? =
        processor.existingDestinationName(track)

    override suspend fun trim(
        track: Track,
        startMs: Long,
        endMs: Long,
        durationMs: Long,
        removeSelectedSegment: Boolean,
        overwriteExisting: Boolean,
    ): Track {
        require(startMs >= 0 && endMs > startMs) { "El tramo seleccionado no es válido." }
        require(durationMs > 0 && endMs <= durationMs) {
            "El tramo seleccionado supera la duración del archivo."
        }
        if (removeSelectedSegment) {
            require(startMs > 0 || endMs < durationMs) {
                "No se puede quitar toda la canción."
            }
        }

        val outputDurationMs = if (removeSelectedSegment) {
            durationMs - (endMs - startMs)
        } else {
            endMs - startMs
        }
        val audioArguments = if (removeSelectedSegment) {
            removalFilter(startMs, endMs, durationMs)
        } else {
            keepFilter(startMs, endMs)
        }
        return processor.process(track, outputDurationMs, overwriteExisting, audioArguments)
    }

    private fun removalFilter(startMs: Long, endMs: Long, durationMs: Long): List<String> {
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

    private fun keepFilter(startMs: Long, endMs: Long): List<String> = listOf(
        "-filter_complex",
        "[0:a:0]atrim=start=${seconds(startMs)}:end=${seconds(endMs)}," +
            "asetpts=PTS-STARTPTS[outa]",
        "-map",
        "[outa]",
    )

    private fun seconds(ms: Long): String = String.format(Locale.US, "%.3f", ms / 1000.0)
}
