package com.tuapp.eleuthera.ui.player

import com.tuapp.eleuthera.data.library.AudioTrimmer
import com.tuapp.eleuthera.domain.model.Track

/** Keeps the editor's selection rules and delegates file processing to [AudioTrimmer]. */
class TrimEditorController(
    private val trimmer: AudioTrimmer,
) {
    suspend fun load(track: Track): TrimEditorState {
        val duration = track.durationMs.takeIf { it > 0 } ?: trimmer.durationMs(track.uri)
        check(duration > 0) { "No se pudo determinar la duración del archivo." }
        return TrimEditorState(
            durationMs = duration,
            startMs = 0,
            endMs = duration,
            loading = false,
        )
    }

    fun setStart(state: TrimEditorState, ms: Long): TrimEditorState {
        val minimum = 1_000L.coerceAtMost(state.durationMs)
        val start = ms.snapToSecond().coerceIn(
            0,
            (state.endMs - minimum).coerceAtLeast(0),
        )
        return state.copy(startMs = start)
    }

    fun setEnd(state: TrimEditorState, ms: Long): TrimEditorState {
        val minimum = 1_000L.coerceAtMost(state.durationMs)
        val end = ms.snapToSecond().coerceIn(
            (state.startMs + minimum).coerceAtMost(state.durationMs),
            state.durationMs,
        )
        return state.copy(endMs = end)
    }

    fun setMode(state: TrimEditorState, mode: TrimMode): TrimEditorState =
        state.copy(mode = mode)

    suspend fun existingDestinationName(track: Track): String? =
        trimmer.existingDestinationName(track)

    suspend fun save(
        track: Track,
        state: TrimEditorState,
        overwriteExisting: Boolean = false,
    ): Track {
        check(state.durationMs > 0 && state.endMs > state.startMs) {
            "El tramo seleccionado no es válido."
        }
        return trimmer.trim(
            track = track,
            startMs = state.startMs,
            endMs = state.endMs,
            durationMs = state.durationMs,
            removeSelectedSegment = state.mode == TrimMode.REMOVE_SELECTED,
            overwriteExisting = overwriteExisting,
        )
    }

    private fun Long.snapToSecond(): Long = ((this + 500) / 1_000) * 1_000
}
