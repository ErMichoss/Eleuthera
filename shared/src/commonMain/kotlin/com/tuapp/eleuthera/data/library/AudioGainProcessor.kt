package com.tuapp.eleuthera.data.library

import com.tuapp.eleuthera.domain.model.Track

data class AudioGainSegment(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
    val gainDb: Float,
)

/** Platform audio renderer for the shared segmented-volume editor. */
interface AudioGainProcessor {
    suspend fun existingDestinationName(track: Track): String? = null

    suspend fun boostSegments(
        track: Track,
        durationMs: Long,
        segments: List<AudioGainSegment>,
        overwriteExisting: Boolean = false,
    ): Track
}
