package com.tuapp.eleuthera.ui.player

import com.tuapp.eleuthera.domain.model.Track

/** KEEP_SELECTED conserva el intervalo; REMOVE_SELECTED une lo que queda fuera de él. */
enum class TrimMode { KEEP_SELECTED, REMOVE_SELECTED }

data class TrimEditorState(
    val durationMs: Long = 0,
    val startMs: Long = 0,
    val endMs: Long = 0,
    val mode: TrimMode = TrimMode.KEEP_SELECTED,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val updatedTrack: Track? = null,
    val error: String? = null,
)
