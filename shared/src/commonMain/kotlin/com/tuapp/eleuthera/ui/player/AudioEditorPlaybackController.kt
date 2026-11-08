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
import kotlinx.coroutines.delay

interface AudioEditorPreviewPlayer {
    val isPlaying: Boolean
    val positionMs: Long

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
}

/** Coordinates preview behavior shared by Android and desktop audio editors. */
class AudioEditorPlaybackController internal constructor(
    private val player: AudioEditorPreviewPlayer,
) {
    var playheadMs by mutableLongStateOf(0L)
        private set

    var previewing by mutableStateOf(false)
        private set

    private var skipPending = false
    private var playFromSelectedSegment = false
    private var editorState: AudioEditorState? = null

    fun updateEditorState(state: AudioEditorState) {
        editorState = state
        if (state.tab == AudioEditorTab.VOLUME) {
            stop()
        }
    }

    fun onTabOrModeChanged(state: AudioEditorState) {
        updateEditorState(state)
        stop()
    }

    fun onSelectionChanged(state: AudioEditorState) {
        updateEditorState(state)
        skipPending = false
        playFromSelectedSegment = false
    }

    fun onPlayheadDragStart() {
        stop()
    }

    fun onPlayheadChanged(positionMs: Long, state: AudioEditorState) {
        updateEditorState(state)
        player.pause()
        previewing = false
        playheadMs = positionMs.coerceIn(0, state.durationMs)
        player.seekTo(playheadMs)
        skipPending = false
        playFromSelectedSegment = isInRemovedRange(playheadMs, state)
    }

    fun toggle(state: AudioEditorState) {
        updateEditorState(state)
        if (previewing || player.isPlaying) {
            playheadMs = player.positionMs.coerceIn(0, state.durationMs)
            player.pause()
            previewing = false
            return
        }

        if (state.tab == AudioEditorTab.VOLUME || state.durationMs <= 0) return
        val start = when {
            state.tab == AudioEditorTab.LYRICS -> playheadMs.coerceIn(0, state.durationMs)
            state.mode == TrimMode.KEEP_SELECTED && playheadMs >= state.endMs ->
                state.startMs
            state.mode == TrimMode.KEEP_SELECTED ->
                playheadMs.coerceIn(state.startMs, state.endMs)
            else -> playheadMs.coerceIn(0, state.durationMs)
        }
        playheadMs = start
        player.seekTo(start)
        skipPending = false
        playFromSelectedSegment = isInRemovedRange(start, state)
        player.play()
        previewing = true
    }

    fun onPlaybackEnded() {
        val state = editorState ?: return
        if (!previewing || state.tab == AudioEditorTab.VOLUME || state.durationMs <= 0) return
        val start = if (
            state.tab == AudioEditorTab.TRIM &&
            state.mode == TrimMode.KEEP_SELECTED
        ) state.startMs else 0L
        player.seekTo(start)
        playheadMs = start
        skipPending = false
        playFromSelectedSegment = false
        player.play()
    }

    fun release() {
        stop()
        editorState = null
    }

    internal fun updatePosition(state: AudioEditorState) {
        updateEditorState(state)
        if (!previewing || !player.isPlaying || state.durationMs <= 0) return

        val position = player.positionMs.coerceIn(0, state.durationMs)
        if (
            state.tab == AudioEditorTab.TRIM &&
            state.mode == TrimMode.KEEP_SELECTED &&
            position >= state.endMs
        ) {
            player.seekTo(state.startMs)
            playheadMs = state.startMs
            return
        }

        if (
            isInRemovedRange(position, state) &&
            !playFromSelectedSegment &&
            !skipPending
        ) {
            val nextPosition = state.endMs.takeIf { it < state.durationMs } ?: 0L
            player.seekTo(nextPosition)
            playheadMs = nextPosition
            skipPending = true
            return
        }

        playheadMs = position
        if (!isInRemovedRange(position, state)) {
            skipPending = false
            playFromSelectedSegment = false
        }
    }

    private fun stop() {
        if (player.isPlaying) player.pause()
        previewing = false
        skipPending = false
        playFromSelectedSegment = false
    }

    private fun isInRemovedRange(positionMs: Long, state: AudioEditorState): Boolean =
        state.tab == AudioEditorTab.TRIM &&
            state.mode == TrimMode.REMOVE_SELECTED &&
            state.hasAudioAfterEdit &&
            state.startMs < state.endMs &&
            positionMs in state.startMs until state.endMs
}

@Composable
fun rememberAudioEditorPlaybackController(
    player: AudioEditorPreviewPlayer,
    state: AudioEditorState,
): AudioEditorPlaybackController {
    val controller = remember(player) { AudioEditorPlaybackController(player) }
    val latestState by rememberUpdatedState(state)

    LaunchedEffect(controller) {
        while (true) {
            controller.updatePosition(latestState)
            delay(50)
        }
    }
    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    return controller
}
