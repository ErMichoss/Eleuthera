package com.tuapp.eleuthera.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.eleuthera.data.settings.LoadedSkin
import com.tuapp.eleuthera.data.settings.ProgressSkinRepository
import com.tuapp.eleuthera.domain.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AudioEditorViewModel @Inject constructor(
    private val controller: AudioEditorController,
    skins: ProgressSkinRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AudioEditorState())
    val state: StateFlow<AudioEditorState> = _state.asStateFlow()

    val progressSkin: StateFlow<LoadedSkin?> =
        skins.active.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun load(track: Track) {
        _state.value = AudioEditorState(loading = true, trackUri = track.uri.toString())
        viewModelScope.launch {
            try {
                _state.value = controller.load(track)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = AudioEditorState(
                    loading = false,
                    error = e.message ?: "No se pudo abrir el archivo de audio.",
                )
            }
        }
    }

    fun setStart(ms: Long) {
        _state.value = controller.setStart(_state.value, ms)
    }

    fun setEnd(ms: Long) {
        _state.value = controller.setEnd(_state.value, ms)
    }

    fun setMode(mode: TrimMode) {
        _state.value = controller.setMode(_state.value, mode)
    }

    fun setTab(tab: AudioEditorTab) {
        _state.value = controller.setTab(_state.value, tab)
    }

    fun addGainSegment() {
        _state.value = controller.addGainSegment(_state.value)
    }

    fun removeGainSegment(id: Long) {
        _state.value = controller.removeGainSegment(_state.value, id)
    }

    fun setGainSegmentRange(id: Long, startMs: Long, endMs: Long) {
        _state.value = controller.setGainSegmentRange(_state.value, id, startMs, endMs)
    }

    fun setGainSegmentGain(id: Long, gainDb: Float) {
        _state.value = controller.setGainSegmentGain(_state.value, id, gainDb)
    }

    fun setLyricsOffset(offsetMs: Long) {
        _state.value = controller.setLyricsOffset(_state.value, offsetMs)
    }

    fun setLyricsLineStart(cueId: Int, startMs: Long) {
        _state.value = controller.setLyricsLineStart(_state.value, cueId, startMs)
    }

    fun save(track: Track) {
        val current = _state.value
        if (current.loading || current.saving || current.saved) return
        // Capturar el estado evita que cambios posteriores alteren la operación ya iniciada.
        _state.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                val result = controller.save(track, current)
                _state.value = _state.value.copy(
                    saving = false,
                    saved = true,
                    savedTab = current.tab,
                    updatedTrack = result.updatedTrack,
                    savedLyrics = result.savedLyrics,
                    saveMessage = result.message,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    saving = false,
                    error = e.message ?: "No se pudo guardar la edición.",
                )
            }
        }
    }
}
