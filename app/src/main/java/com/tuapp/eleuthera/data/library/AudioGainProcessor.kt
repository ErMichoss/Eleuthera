package com.tuapp.eleuthera.data.library

import com.tuapp.eleuthera.domain.model.Track
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class AudioGainSegment(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
    val gainDb: Float,
)

@Singleton
class AudioGainProcessor @Inject constructor(
    private val processor: AudioFileProcessor,
) {
    suspend fun boostSegments(
        track: Track,
        durationMs: Long,
        segments: List<AudioGainSegment>,
    ): Track {
        require(durationMs > 0) { "La duración del audio no es válida." }
        require(segments.isNotEmpty()) { "Añade al menos un segmento de volumen." }
        segments.forEach { segment ->
            require(segment.startMs >= 0 && segment.endMs > segment.startMs && segment.endMs <= durationMs) {
                "Hay un segmento de volumen fuera de los límites de la canción."
            }
            require(segment.gainDb in MIN_GAIN_DB..MAX_GAIN_DB) {
                "El aumento de volumen debe estar entre 0 y 12 dB."
            }
        }

        return processor.process(track, durationMs) {
            val terms = segments.map { segment ->
                val startSeconds = String.format(Locale.US, "%.3f", segment.startMs / 1_000.0)
                val endSeconds = String.format(Locale.US, "%.3f", segment.endMs / 1_000.0)
                val linearGain = Math.pow(10.0, segment.gainDb / 20.0)
                val extraGain = linearGain - 1.0
                val extraGainStr = String.format(Locale.US, "%.6f", extraGain)
                "($extraGainStr*between(t,$startSeconds,$endSeconds))"
            }
            val volumeExpr = "1+" + terms.joinToString("+")
            listOf("-af", "volume=$volumeExpr:eval=frame,alimiter=limit=0.95:level=disabled", "-map", "0:a:0")
        }
    }

    private companion object {
        const val MIN_GAIN_DB = 0f
        const val MAX_GAIN_DB = 12f
    }
}
