package com.tuapp.eleuthera.ui.player

import com.tuapp.eleuthera.data.library.AudioGainProcessor
import com.tuapp.eleuthera.data.library.AudioGainSegment
import com.tuapp.eleuthera.data.library.AudioTrimmer
import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.ui.lyrics.LyricsStorage
import com.tuapp.eleuthera.ui.lyrics.LyricsTrack
import javax.inject.Inject

/** Shared editor behavior; audio processing and lyrics I/O are provided per platform. */
class AudioEditorController @Inject constructor(
    private val trimmer: AudioTrimmer,
    private val gainProcessor: AudioGainProcessor,
    private val lyricsStorage: LyricsStorage,
) {
    suspend fun load(track: Track): AudioEditorState {
        val duration = track.durationMs.takeIf { it > 0 } ?: trimmer.durationMs(track.uri)
        check(duration > 0) { "No se pudo determinar la duración del archivo." }
        val localLyrics = lyricsStorage.read(track.toLyricsTrack(duration))
        val lyricsError = if (localLyrics.text.isNullOrBlank()) {
            when {
                localLyrics.embeddedReadError != null || localLyrics.sidecarReadError != null ->
                    "No se pudieron leer las letras locales de esta canción."
                else -> "No se encontraron letras locales para editar."
            }
        } else {
            null
        }
        return AudioEditorState(
            trackUri = track.uri.toString(),
            durationMs = duration,
            startMs = 0,
            endMs = duration,
            loading = false,
            lyricsText = localLyrics.text,
            lyricsError = lyricsError,
        )
    }

    suspend fun existingDestinationName(track: Track, tab: AudioEditorTab): String? =
        when (tab) {
            AudioEditorTab.TRIM -> trimmer.existingDestinationName(track)
            AudioEditorTab.VOLUME -> gainProcessor.existingDestinationName(track)
            AudioEditorTab.LYRICS -> null
        }

    fun setStart(state: AudioEditorState, ms: Long): AudioEditorState {
        val minimum = 1_000L.coerceAtMost(state.durationMs)
        val start = ms.snapToSecond().coerceIn(
            0,
            (state.endMs - minimum).coerceAtLeast(0),
        )
        return state.copy(startMs = start)
    }

    fun setEnd(state: AudioEditorState, ms: Long): AudioEditorState {
        val minimum = 1_000L.coerceAtMost(state.durationMs)
        val end = ms.snapToSecond().coerceIn(
            (state.startMs + minimum).coerceAtMost(state.durationMs),
            state.durationMs,
        )
        return state.copy(endMs = end)
    }

    fun setMode(state: AudioEditorState, mode: TrimMode): AudioEditorState =
        state.copy(mode = mode)

    fun setTab(state: AudioEditorState, tab: AudioEditorTab): AudioEditorState =
        state.copy(tab = tab, error = null)

    fun addGainSegment(state: AudioEditorState): AudioEditorState {
        if (state.gainSegments.size >= MAX_GAIN_SEGMENTS) {
            return state.copy(error = "No puedes añadir más de $MAX_GAIN_SEGMENTS segmentos.")
        }
        val start = state.gainSegments.lastOrNull()?.endMs ?: 0L
        if (start >= state.durationMs) {
            return state.copy(error = "No queda tiempo disponible para otro segmento.")
        }
        val end = (start + SEGMENT_DEFAULT_LENGTH_MS).coerceAtMost(state.durationMs)
        return state.copy(
            gainSegments = state.gainSegments + AudioGainSegment(
                id = state.gainSegments.maxOfOrNull { it.id + 1 } ?: 1L,
                startMs = start,
                endMs = end,
                gainDb = DEFAULT_GAIN_DB,
            ),
            error = null,
        )
    }

    fun removeGainSegment(state: AudioEditorState, id: Long): AudioEditorState =
        state.copy(gainSegments = state.gainSegments.filterNot { it.id == id }, error = null)

    fun setGainSegmentRange(
        state: AudioEditorState,
        id: Long,
        startMs: Long,
        endMs: Long,
    ): AudioEditorState {
        val minimum = 1_000L.coerceAtMost(state.durationMs)
        val start = startMs.snapToSecond().coerceIn(0, (endMs - minimum).coerceAtLeast(0))
        val end = endMs.snapToSecond().coerceIn(
            (start + minimum).coerceAtMost(state.durationMs),
            state.durationMs,
        )
        return state.copy(
            gainSegments = state.gainSegments.map {
                if (it.id == id) it.copy(startMs = start, endMs = end) else it
            },
            error = null,
        )
    }

    fun setGainSegmentGain(state: AudioEditorState, id: Long, gainDb: Float): AudioEditorState =
        state.copy(
            gainSegments = state.gainSegments.map {
                if (it.id == id) it.copy(gainDb = gainDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)) else it
            },
            error = null,
        )

    fun setLyricsOffset(state: AudioEditorState, offsetMs: Long): AudioEditorState =
        state.copy(lyricsOffsetMs = offsetMs.coerceIn(-MAX_LYRICS_OFFSET_MS, MAX_LYRICS_OFFSET_MS))

    fun setLyricsLineStart(state: AudioEditorState, cueId: Int, startMs: Long): AudioEditorState {
        val lyrics = state.lyricsText?.takeIf(String::isNotBlank)
            ?: error(state.lyricsError ?: "No hay letras para ajustar.")
        val currentLyrics = LyricsTimestampEditor.shift(lyrics, state.lyricsOffsetMs, state.durationMs)
        val updatedLyrics = LyricsTimestampEditor.setLineStart(
            currentLyrics,
            cueId,
            startMs,
            state.durationMs,
        )
        return state.copy(
            lyricsText = updatedLyrics,
            lyricsOffsetMs = 0,
            lyricsLineEdited = true,
        )
    }

    suspend fun save(
        track: Track,
        state: AudioEditorState,
        overwriteExisting: Boolean = false,
    ): AudioEditorSaveResult = when (state.tab) {
        AudioEditorTab.TRIM -> {
            check(state.durationMs > 0 && state.endMs > state.startMs) {
                "El tramo seleccionado no es válido."
            }
            check(
                state.mode != TrimMode.REMOVE_SELECTED ||
                    state.startMs > 0 || state.endMs < state.durationMs,
            ) {
                "No se puede quitar toda la canción."
            }
            AudioEditorSaveResult(
                updatedTrack = trimmer.trim(
                    track = track,
                    startMs = state.startMs,
                    endMs = state.endMs,
                    durationMs = state.durationMs,
                    removeSelectedSegment = state.mode == TrimMode.REMOVE_SELECTED,
                    overwriteExisting = overwriteExisting,
                ),
            )
        }
        AudioEditorTab.VOLUME -> AudioEditorSaveResult(
            updatedTrack = gainProcessor.boostSegments(
                track,
                state.durationMs,
                state.gainSegments,
                overwriteExisting,
            ),
        )
        AudioEditorTab.LYRICS -> {
            val lyrics = state.lyricsText?.takeIf(String::isNotBlank)
                ?: error(state.lyricsError ?: "No hay letras para ajustar.")
            val shifted = LyricsTimestampEditor.shift(lyrics, state.lyricsOffsetMs, state.durationMs)
            val message = lyricsStorage.save(
                track = track.toLyricsTrack(state.durationMs),
                lyrics = shifted,
                isStillCurrent = { true },
                replaceExisting = true,
            )
            AudioEditorSaveResult(savedLyrics = shifted, message = message)
        }
    }

    private fun Long.snapToSecond(): Long = ((this + 500) / 1_000) * 1_000

    private fun Track.toLyricsTrack(durationMs: Long) = LyricsTrack(
        uri = uri.toString(),
        title = title,
        artist = artist,
        durationMs = durationMs,
    )

    private companion object {
        const val SEGMENT_DEFAULT_LENGTH_MS = 5_000L
        const val DEFAULT_GAIN_DB = 3f
        const val MIN_GAIN_DB = 0f
        const val MAX_GAIN_DB = 12f
        const val MAX_LYRICS_OFFSET_MS = 30_000L
        const val MAX_GAIN_SEGMENTS = 20
    }
}
