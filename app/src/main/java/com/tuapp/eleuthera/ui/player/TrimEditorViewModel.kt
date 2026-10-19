package com.tuapp.eleuthera.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.eleuthera.data.library.AudioTrimmer
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

@HiltViewModel
class TrimEditorViewModel @Inject constructor(
    private val trimmer: AudioTrimmer,
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
                val duration = track.durationMs.takeIf { it > 0 }
                    ?: trimmer.durationMs(track.uri)
                if (duration <= 0) {
                    _state.value = TrimEditorState(
                        loading = false,
                        error = "No se pudo determinar la duración del archivo.",
                    )
                    return@launch
                }
                _state.value = _state.value.copy(
                    durationMs = duration,
                    startMs = 0,
                    endMs = duration,
                    loading = false,
                )
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
        val current = _state.value
        // Los límites se editan a segundos enteros y siempre dejan al menos un segundo de audio.
        val minimum = 1_000L.coerceAtMost(current.durationMs)
        val start = ms.snapToSecond().coerceIn(0, (current.endMs - minimum).coerceAtLeast(0))
        _state.value = current.copy(startMs = start)
    }

    fun setEnd(ms: Long) {
        val current = _state.value
        val minimum = 1_000L.coerceAtMost(current.durationMs)
        val end = ms.snapToSecond().coerceIn(
            (current.startMs + minimum).coerceAtMost(current.durationMs),
            current.durationMs,
        )
        _state.value = current.copy(endMs = end)
    }

    fun setMode(mode: TrimMode) {
        _state.value = _state.value.copy(mode = mode)
    }

    fun save(track: Track) {
        val current = _state.value
        if (current.loading || current.saving || current.saved) return
        // Capturar el estado evita que cambios posteriores alteren la operación ya iniciada.
        _state.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                val updatedTrack = trimmer.trim(
                    track = track,
                    startMs = current.startMs,
                    endMs = current.endMs,
                    durationMs = current.durationMs,
                    removeSelectedSegment = current.mode == TrimMode.REMOVE_SELECTED,
                )
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

private fun Long.snapToSecond(): Long = ((this + 500) / 1_000) * 1_000
