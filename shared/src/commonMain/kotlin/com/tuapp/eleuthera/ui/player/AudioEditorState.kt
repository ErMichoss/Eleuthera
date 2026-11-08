package com.tuapp.eleuthera.ui.player

import com.tuapp.eleuthera.data.library.AudioGainSegment
import com.tuapp.eleuthera.domain.model.Track

enum class AudioEditorTab { TRIM, LYRICS, VOLUME }

data class AudioEditorState(
    val trackUri: String? = null,
    val durationMs: Long = 0,
    val startMs: Long = 0,
    val endMs: Long = 0,
    val mode: TrimMode = TrimMode.KEEP_SELECTED,
    val tab: AudioEditorTab = AudioEditorTab.TRIM,
    val gainSegments: List<AudioGainSegment> = emptyList(),
    val lyricsText: String? = null,
    val lyricsOffsetMs: Long = 0,
    val lyricsLineEdited: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val savedTab: AudioEditorTab? = null,
    val savedLyrics: String? = null,
    val saveMessage: String? = null,
    val updatedTrack: Track? = null,
    val error: String? = null,
    val lyricsError: String? = null,
) {
    val hasAudioAfterEdit: Boolean
        get() = mode != TrimMode.REMOVE_SELECTED ||
            startMs > 0 || endMs < durationMs
}

data class AudioEditorSaveResult(
    val updatedTrack: Track? = null,
    val savedLyrics: String? = null,
    val message: String? = null,
)
