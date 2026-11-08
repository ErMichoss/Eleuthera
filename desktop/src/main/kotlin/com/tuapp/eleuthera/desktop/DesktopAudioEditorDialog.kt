package com.tuapp.eleuthera.desktop

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tuapp.eleuthera.data.settings.DesktopProgressSkinStore
import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.platform.FileUri
import com.tuapp.eleuthera.playback.DesktopPlayerController
import com.tuapp.eleuthera.playback.Ffmpeg
import com.tuapp.eleuthera.playback.FfmpegAudioPlayer
import com.tuapp.eleuthera.ui.player.AudioEditorContent
import com.tuapp.eleuthera.ui.player.AudioEditorController
import com.tuapp.eleuthera.ui.player.AudioEditorSaveResult
import com.tuapp.eleuthera.ui.player.AudioEditorState
import com.tuapp.eleuthera.ui.player.AudioEditorPreviewPlayer
import com.tuapp.eleuthera.ui.player.rememberAudioEditorPlaybackController
import kotlinx.coroutines.launch

@Composable
fun DesktopAudioEditorDialog(
    track: Track,
    controller: AudioEditorController,
    skinStore: DesktopProgressSkinStore,
    player: DesktopPlayerController,
    onClose: () -> Unit,
) {
    val skin by skinStore.active.collectAsState(null)
    val scope = rememberCoroutineScope()
    var state by remember(track.id) { mutableStateOf(AudioEditorState()) }
    var overwriteDestinationName by remember(track.id) { mutableStateOf<String?>(null) }
    var onPreviewEnded by remember { mutableStateOf<() -> Unit>({}) }
    val latestOnPreviewEnded by rememberUpdatedState(onPreviewEnded)
    val sourceFile = (track.uri as? FileUri)?.file
    val preview = remember(track.id) {
        FfmpegAudioPlayer(
            ffmpegPath = Ffmpeg::find,
            onEnded = { scope.launch { latestOnPreviewEnded() } },
            onError = { message -> scope.launch { state = state.copy(error = message) } },
        ).apply {
            sourceFile?.let { load(it, autoplay = false) }
        }
    }
    val previewPlayer = remember(preview) {
        object : AudioEditorPreviewPlayer {
            override val isPlaying: Boolean get() = preview.isPlaying
            override val positionMs: Long get() = preview.positionMs
            override fun play() = preview.play()
            override fun pause() = preview.pause()
            override fun seekTo(positionMs: Long) = preview.seekTo(positionMs)
        }
    }
    val playback = rememberAudioEditorPlaybackController(previewPlayer, state)
    SideEffect { onPreviewEnded = playback::onPlaybackEnded }

    val initialPlayerState = remember { player.state.value }
    var allowedMediaId by remember { mutableStateOf(initialPlayerState.mediaId) }
    val latestAllowedMediaId by rememberUpdatedState(allowedMediaId)
    var pausedByEditor by remember { mutableStateOf(false) }
    val playerState by player.state.collectAsState()

    LaunchedEffect(player) {
        if (initialPlayerState.isPlaying && player.state.value.isPlaying) {
            player.toggle()
            pausedByEditor = true
        }
    }
    DisposableEffect(player) {
        onDispose {
            if (
                pausedByEditor &&
                initialPlayerState.isPlaying &&
                player.state.value.mediaId == latestAllowedMediaId &&
                !player.state.value.isPlaying
            ) {
                player.toggle()
            }
        }
    }
    DisposableEffect(preview) {
        onDispose { preview.stop() }
    }

    LaunchedEffect(track) {
        try {
            state = controller.load(track)
        } catch (e: Exception) {
            state = AudioEditorState(
                loading = false,
                error = e.message ?: "Error al cargar el editor de audio.",
            )
        }
    }

    LaunchedEffect(playerState.mediaId, allowedMediaId) {
        if (playerState.mediaId != allowedMediaId) {
            playback.onPlayheadDragStart()
            onClose()
        }
    }

    val playerHasTrackBeingEdited = player.state.value.mediaId == track.id.toString()

    fun closeEditor() {
        playback.onPlayheadDragStart()
        onClose()
    }

    suspend fun finishSave(current: AudioEditorState, result: AudioEditorSaveResult) {
        val updatedTrack = result.updatedTrack
        if (updatedTrack != null) {
            if (playerHasTrackBeingEdited) allowedMediaId = updatedTrack.id.toString()
            player.updateTrack(track.id, updatedTrack)
        }
        state = state.copy(
            saving = false,
            saved = true,
            savedTab = current.tab,
            updatedTrack = updatedTrack,
            savedLyrics = result.savedLyrics,
            saveMessage = result.message,
        )
    }

    fun save(current: AudioEditorState, overwriteExisting: Boolean) {
        scope.launch {
            try {
                val result = controller.save(track, current, overwriteExisting)
                finishSave(current, result)
            } catch (e: Exception) {
                state = state.copy(
                    saving = false,
                    error = e.message ?: "No se pudo guardar la edición.",
                )
            }
        }
    }

    fun requestSave() {
        val current = state
        if (current.loading || current.saving || current.saved) return
        playback.onPlayheadDragStart()
        state = current.copy(saving = true, error = null)
        scope.launch {
            try {
                val destinationName = controller.existingDestinationName(track, current.tab)
                if (destinationName != null) {
                    overwriteDestinationName = destinationName
                    state = state.copy(saving = false)
                } else {
                    save(current, overwriteExisting = false)
                }
            } catch (e: Exception) {
                state = state.copy(
                    saving = false,
                    error = e.message ?: "Error al comprobar el archivo de destino.",
                )
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (!state.saving && overwriteDestinationName == null) closeEditor()
        },
    ) {
        Surface(
            modifier = Modifier.width(760.dp).height(820.dp).padding(16.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            AudioEditorContent(
                track = track,
                state = state,
                skin = skin,
                playheadMs = playback.playheadMs,
                previewing = playback.previewing && preview.isPlaying,
                hasAudioAfterEdit = state.hasAudioAfterEdit,
                onClose = {
                    if (!state.saving && overwriteDestinationName == null) closeEditor()
                },
                onTabChange = { tab ->
                    state = controller.setTab(state, tab)
                    playback.onTabOrModeChanged(state)
                },
                onModeChange = { mode ->
                    state = controller.setMode(state, mode)
                    playback.onTabOrModeChanged(state)
                },
                onRangeChange = { start, end ->
                    state = controller.setStart(state, start)
                    state = controller.setEnd(state, end)
                    playback.onSelectionChanged(state)
                },
                onPlayheadChange = { position -> playback.onPlayheadChanged(position, state) },
                onPlayheadDragStart = playback::onPlayheadDragStart,
                onStepStart = { step ->
                    state = controller.setStart(state, state.startMs + step)
                    playback.onSelectionChanged(state)
                },
                onStepEnd = { step ->
                    state = controller.setEnd(state, state.endMs + step)
                    playback.onSelectionChanged(state)
                },
                onSegmentRangeChange = { id, start, end ->
                    state = controller.setGainSegmentRange(state, id, start, end)
                },
                onSegmentGainChange = { id, gain ->
                    state = controller.setGainSegmentGain(state, id, gain)
                },
                onAddSegment = { state = controller.addGainSegment(state) },
                onRemoveSegment = { id -> state = controller.removeGainSegment(state, id) },
                onLyricsOffsetChange = { offset ->
                    state = controller.setLyricsOffset(state, offset)
                },
                onLyricsLineStartChange = { id, start ->
                    try {
                        state = controller.setLyricsLineStart(state, id, start)
                    } catch (e: Exception) {
                        state = state.copy(error = e.message ?: "No se pudo cambiar la marca de tiempo.")
                    }
                },
                onTogglePreview = { playback.toggle(state) },
                onSave = ::requestSave,
            )
        }
    }

    overwriteDestinationName?.let { destinationName ->
        AlertDialog(
            onDismissRequest = { overwriteDestinationName = null },
            title = { Text("El archivo ya existe") },
            text = {
                Text(
                    "Ya existe \"$destinationName\" en esta carpeta. ¿Quieres sobrescribirlo? " +
                        "El archivo existente se reemplazará por el audio editado.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        overwriteDestinationName = null
                        val current = state
                        if (!current.loading && !current.saving && !current.saved) {
                            state = current.copy(saving = true, error = null)
                            save(current, overwriteExisting = true)
                        }
                    },
                ) { Text("Sobrescribir") }
            },
            dismissButton = {
                TextButton(onClick = { overwriteDestinationName = null }) {
                    Text("Cancelar")
                }
            },
        )
    }
}
