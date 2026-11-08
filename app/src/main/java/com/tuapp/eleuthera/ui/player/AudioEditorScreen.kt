package com.tuapp.eleuthera.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.tuapp.eleuthera.domain.model.Track

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
    val preview = remember(track.uri) { ExoPlayer.Builder(context).build() }
    val previewPlayer = remember(preview) {
        object : AudioEditorPreviewPlayer {
            override val isPlaying: Boolean get() = preview.isPlaying
            override val positionMs: Long get() = preview.currentPosition
            override fun play() = preview.play()
            override fun pause() = preview.pause()
            override fun seekTo(positionMs: Long) = preview.seekTo(positionMs)
        }
    }
    val playback = rememberAudioEditorPlaybackController(previewPlayer, state)

    LaunchedEffect(track.id) { vm.load(track) }
    LaunchedEffect(track.uri, state.trackUri, state.savedTab, state.savedLyrics) {
        if (state.trackUri == track.uri.toString() &&
            state.savedTab == AudioEditorTab.LYRICS
        ) {
            state.savedLyrics?.let { onLyricsSaved(track.uri.toString(), it) }
        }
    }
    DisposableEffect(preview, track.uri) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) playback.onPlaybackEnded()
            }
        }
        preview.addListener(listener)
        preview.setMediaItem(MediaItem.fromUri(track.uri))
        preview.prepare()
        preview.repeatMode = Player.REPEAT_MODE_OFF
        onDispose {
            preview.removeListener(listener)
            preview.release()
        }
    }

    AudioEditorContent(
        track = track,
        state = state,
        skin = skin,
        playheadMs = playback.playheadMs,
        previewing = playback.previewing,
        hasAudioAfterEdit = state.hasAudioAfterEdit,
        modifier = modifier,
        onClose = { onClose(state.updatedTrack) },
        onTabChange = { tab ->
            vm.setTab(tab)
            playback.onTabOrModeChanged(vm.state.value)
        },
        onModeChange = { mode ->
            vm.setMode(mode)
            playback.onTabOrModeChanged(vm.state.value)
        },
        onRangeChange = { start, end ->
            vm.setStart(start)
            vm.setEnd(end)
            playback.onSelectionChanged(vm.state.value)
        },
        onPlayheadChange = { position -> playback.onPlayheadChanged(position, state) },
        onPlayheadDragStart = playback::onPlayheadDragStart,
        onStepStart = { step ->
            vm.setStart(state.startMs + step)
            playback.onSelectionChanged(vm.state.value)
        },
        onStepEnd = { step ->
            vm.setEnd(state.endMs + step)
            playback.onSelectionChanged(vm.state.value)
        },
        onSegmentRangeChange = vm::setGainSegmentRange,
        onSegmentGainChange = vm::setGainSegmentGain,
        onAddSegment = vm::addGainSegment,
        onRemoveSegment = vm::removeGainSegment,
        onLyricsOffsetChange = vm::setLyricsOffset,
        onLyricsLineStartChange = vm::setLyricsLineStart,
        onTogglePreview = { playback.toggle(state) },
        onSave = { vm.save(track) },
    )
}
