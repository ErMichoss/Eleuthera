package com.tuapp.eleuthera.ui.lyrics

import androidx.compose.runtime.Composable
import com.tuapp.eleuthera.ui.player.LyricsContent

/** Android wrapper that binds player identity/state to the shared lyrics content. */
@Composable
fun LyricsScreen(
    state: LyricsUiState,
    currentMediaUri: String?,
    positionMs: Long,
) {
    val isCurrentTrack = state.mediaUri == currentMediaUri
    LyricsContent(
        text = state.text.takeIf { isCurrentTrack },
        loading = state.loading || !isCurrentTrack,
        error = state.error.takeIf { isCurrentTrack },
        positionMs = positionMs,
        subtitleMessage = state.subtitleMessage.takeIf { isCurrentTrack },
    )
}
