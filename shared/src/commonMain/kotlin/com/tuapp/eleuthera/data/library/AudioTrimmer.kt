package com.tuapp.eleuthera.data.library

import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.platform.PlatformUri

/** Interfaz compartida para recortar archivos de audio en Android y Escritorio. */
interface AudioTrimmer {
    suspend fun durationMs(uri: PlatformUri): Long
    suspend fun existingDestinationName(track: Track): String? = null
    suspend fun trim(
        track: Track,
        startMs: Long,
        endMs: Long,
        durationMs: Long,
        removeSelectedSegment: Boolean,
        overwriteExisting: Boolean = false,
    ): Track
}
