package com.tuapp.eleuthera.data.library

import com.tuapp.eleuthera.domain.model.Track
import java.util.Locale
import kotlin.math.pow

/** Applies the shared editor's per-segment gain curve with FFmpeg on desktop. */
class DesktopAudioGainProcessor(
    private val processor: DesktopAudioFileProcessor,
) : AudioGainProcessor {
    override suspend fun existingDestinationName(track: Track): String? =
        processor.existingDestinationName(track)

    override suspend fun boostSegments(
        track: Track,
        durationMs: Long,
        segments: List<AudioGainSegment>,
        overwriteExisting: Boolean,
    ): Track {
        require(durationMs > 0) { "La duración del audio no es válida." }
        require(segments.isNotEmpty()) { "Añade al menos un segmento de volumen." }
        segments.forEach { segment ->
            require(
                segment.startMs >= 0 &&
                    segment.endMs > segment.startMs &&
                    segment.endMs <= durationMs,
            ) {
                "Hay un segmento de volumen fuera de los límites de la canción."
            }
            require(segment.gainDb in MIN_GAIN_DB..MAX_GAIN_DB) {
                "El aumento de volumen debe estar entre 0 y 12 dB."
            }
        }

        val terms = segments.map { segment ->
            val startSeconds = seconds(segment.startMs)
            val endSeconds = seconds(segment.endMs)
            val extraGain = 10.0.pow(segment.gainDb / 20.0) - 1.0
            val extraGainStr = String.format(Locale.US, "%.6f", extraGain)
            "($extraGainStr*between(t,$startSeconds,$endSeconds))"
        }
        val volumeExpr = "1+" + terms.joinToString("+")
        val audioArguments = listOf(
            "-af",
            "volume=$volumeExpr:eval=frame,alimiter=limit=0.95:level=disabled",
            "-map",
            "0:a:0",
        )
        return processor.process(track, durationMs, overwriteExisting, audioArguments)
    }

    private fun seconds(ms: Long): String = String.format(Locale.US, "%.3f", ms / 1_000.0)

    private companion object {
        const val MIN_GAIN_DB = 0f
        const val MAX_GAIN_DB = 12f
    }
}
