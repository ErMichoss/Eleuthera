package com.tuapp.eleuthera.ui.lyrics

import androidx.compose.runtime.Composable
import com.tuapp.eleuthera.data.lrclib.LrclibLyrics
import com.tuapp.eleuthera.ui.player.LyricsContent

/** Android wrapper that binds player identity/state to the shared lyrics content. */
@Composable
fun LyricsScreen(
    state: LyricsUiState,
    currentMediaUri: String?,
    positionMs: Long,
    onSearchMore: () -> Unit,
    onSearchManual: (String, String) -> Unit,
    onSelectCandidate: (LrclibLyrics) -> Unit,
) {
    val isCurrentTrack = state.mediaUri == currentMediaUri
    LyricsContent(
        text = state.text.takeIf { isCurrentTrack },
        loading = state.loading || !isCurrentTrack,
        error = state.error.takeIf { isCurrentTrack },
        positionMs = positionMs,
        subtitleMessage = state.subtitleMessage.takeIf { isCurrentTrack },
        candidates = state.candidates.takeIf { isCurrentTrack }.orEmpty(),
        searchingCandidates = state.searchingCandidates && isCurrentTrack,
        candidateSearchError = state.candidateSearchError.takeIf { isCurrentTrack },
        trackTitle = state.trackTitle.takeIf { isCurrentTrack }.orEmpty(),
        trackArtist = state.trackArtist.takeIf { isCurrentTrack }.orEmpty(),
        onSearchMore = onSearchMore,
        onSearchManual = onSearchManual,
        onSelectCandidate = onSelectCandidate,
    )
}
