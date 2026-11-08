package com.tuapp.eleuthera.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.tuapp.eleuthera.domain.model.Track
import kotlinx.coroutines.delay

/** Android integration: owns the ViewModel connection and ExoPlayer preview lifecycle. */
@Composable
fun AudioEditorScreen(
    track: Track,
    onClose: (Track?) -> Unit,
    onLyricsSaved: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    vm: AudioEditorViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val skin by vm.progressSkin.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var previewing by remember(track.id) { mutableStateOf(false) }
    var playheadMs by remember(track.id) { mutableLongStateOf(0L) }
    var skipPending by remember { mutableStateOf(false) }
    var playFromSelectedSegment by remember { mutableStateOf(false) }
    val currentPlayFromSelectedSegment by rememberUpdatedState(playFromSelectedSegment)
    val preview = remember(track.uri) { ExoPlayer.Builder(context).build() }
    val hasAudioAfterEdit =
        state.mode != TrimMode.REMOVE_SELECTED ||
            state.startMs > 0 || state.endMs < state.durationMs

    LaunchedEffect(track.id) { vm.load(track) }
    LaunchedEffect(track.uri, state.trackUri, state.savedTab, state.savedLyrics) {
        if (state.trackUri == track.uri.toString() &&
            state.savedTab == AudioEditorTab.LYRICS
        ) {
            state.savedLyrics?.let { onLyricsSaved(track.uri.toString(), it) }
        }
    }
    DisposableEffect(preview, track.uri) {
        preview.setMediaItem(MediaItem.fromUri(track.uri))
        preview.prepare()
        onDispose { preview.release() }
    }
    LaunchedEffect(preview, state.mode, state.tab) {
        skipPending = false
        if (state.tab == AudioEditorTab.VOLUME) {
            preview.pause()
            previewing = false
        }
        preview.repeatMode =
            if (state.tab == AudioEditorTab.LYRICS ||
                (state.tab == AudioEditorTab.TRIM && state.mode == TrimMode.REMOVE_SELECTED)
            ) Player.REPEAT_MODE_ONE
            else Player.REPEAT_MODE_OFF
    }
    LaunchedEffect(previewing, state.startMs, state.endMs, state.durationMs, state.mode, state.tab) {
        if (previewing) {
            skipPending = false
            while (previewing) {
                val position = preview.currentPosition
                if (state.tab == AudioEditorTab.LYRICS) {
                    playheadMs = position
                } else if (state.mode == TrimMode.REMOVE_SELECTED && hasAudioAfterEdit) {
                    if (position in state.startMs until state.endMs) {
                        // Una búsqueda manual puede escuchar el intervalo; la reproducción normal lo omite.
                        if (!currentPlayFromSelectedSegment && !skipPending) {
                            preview.seekTo(state.endMs)
                            skipPending = true
                        } else {
                            playheadMs = position
                        }
                    } else {
                        skipPending = false
                        playheadMs = position
                        playFromSelectedSegment = false
                    }
                } else if (state.mode == TrimMode.REMOVE_SELECTED) {
                    playheadMs = position
                } else if (position >= state.endMs) {
                    preview.seekTo(state.startMs)
                    playheadMs = state.startMs
                } else {
                    playheadMs = position.coerceAtLeast(state.startMs)
                }
                delay(50)
            }
        }
    }

    AudioEditorContent(
        track = track,
        state = state,
        skin = skin,
        playheadMs = playheadMs,
        previewing = previewing,
        hasAudioAfterEdit = hasAudioAfterEdit,
        modifier = modifier,
        onClose = { onClose(state.updatedTrack) },
        onTabChange = { tab ->
            preview.pause()
            previewing = false
            playFromSelectedSegment = false
            vm.setTab(tab)
        },
        onModeChange = { mode ->
            preview.pause()
            previewing = false
            playFromSelectedSegment = false
            vm.setMode(mode)
        },
        onRangeChange = { start, end ->
            playFromSelectedSegment = false
            skipPending = false
            vm.setStart(start)
            vm.setEnd(end)
        },
        onPlayheadChange = { position ->
            preview.pause()
            previewing = false
            playheadMs = position
            skipPending = false
            playFromSelectedSegment =
                state.tab == AudioEditorTab.TRIM &&
                    state.mode == TrimMode.REMOVE_SELECTED &&
                    position in state.startMs until state.endMs
            preview.seekTo(position)
        },
        onPlayheadDragStart = {
            preview.pause()
            previewing = false
        },
        onStepStart = { step ->
            playFromSelectedSegment = false
            skipPending = false
            vm.setStart(state.startMs + step)
        },
        onStepEnd = { step ->
            playFromSelectedSegment = false
            skipPending = false
            vm.setEnd(state.endMs + step)
        },
        onSegmentRangeChange = vm::setGainSegmentRange,
        onSegmentGainChange = vm::setGainSegmentGain,
        onAddSegment = vm::addGainSegment,
        onRemoveSegment = vm::removeGainSegment,
        onLyricsOffsetChange = vm::setLyricsOffset,
        onLyricsLineStartChange = vm::setLyricsLineStart,
        onTogglePreview = {
            if (previewing) {
                playheadMs = preview.currentPosition
                preview.pause()
                previewing = false
            } else {
                val startPosition = when {
                    state.tab == AudioEditorTab.LYRICS -> playheadMs.coerceIn(0, state.durationMs)
                    state.mode == TrimMode.KEEP_SELECTED && playheadMs >= state.endMs ->
                        state.startMs
                    else -> playheadMs.coerceIn(
                        if (state.mode == TrimMode.KEEP_SELECTED) state.startMs else 0L,
                        state.durationMs,
                    )
                }
                preview.seekTo(startPosition)
                playheadMs = startPosition
                playFromSelectedSegment =
                    state.tab == AudioEditorTab.TRIM &&
                        state.mode == TrimMode.REMOVE_SELECTED &&
                        startPosition in state.startMs until state.endMs
                preview.play()
                previewing = true
            }
        },
        onSave = { vm.save(track) },
    )
}
