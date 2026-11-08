package com.tuapp.eleuthera.data.library

import android.content.Context
import android.media.MediaMetadataRetriever
import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.platform.PlatformUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AndroidAudioTrimmer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val processor: AudioFileProcessor,
) : AudioTrimmer {
    override suspend fun durationMs(uri: PlatformUri): Long = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(0) ?: 0
        } finally {
            retriever.release()
        }
    }

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
        return processor.process(track, outputDurationMs) {
            if (removeSelectedSegment) {
                removalFilter(startMs, endMs, durationMs)
            } else {
                listOf(
                    "-ss", String.format(Locale.US, "%.3f", startMs / 1000.0),
                    "-t", String.format(Locale.US, "%.3f", (endMs - startMs) / 1000.0),
                    "-map", "0:a:0",
                )
            }
        }
    }

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
}
