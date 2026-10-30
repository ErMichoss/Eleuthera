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
class TrimEditorViewModel @Inject constructor(
    private val controller: TrimEditorController,
    skins: ProgressSkinRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TrimEditorState())
    val state: StateFlow<TrimEditorState> = _state.asStateFlow()

    val progressSkin: StateFlow<LoadedSkin?> =
        skins.active.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun load(track: Track) {
        _state.value = TrimEditorState(loading = true)
        viewModelScope.launch {
            try {
                _state.value = controller.load(track)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = TrimEditorState(
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

    fun save(track: Track) {
        val current = _state.value
        if (current.loading || current.saving || current.saved) return
        // Capturar el estado evita que cambios posteriores alteren la operación ya iniciada.
        _state.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                val updatedTrack = controller.save(track, current)
                _state.value = _state.value.copy(
                    saving = false,
                    saved = true,
                    updatedTrack = updatedTrack,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    saving = false,
                    error = e.message ?: "No se pudo guardar el recorte.",
                )
            }
        }
    }
}
