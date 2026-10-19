package com.tuapp.eleuthera.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.eleuthera.data.settings.LoadedSkin
import com.tuapp.eleuthera.data.settings.ProgressSkinRepository
import com.tuapp.eleuthera.domain.model.ProgressSkin
import com.tuapp.eleuthera.domain.model.SkinSlot
import com.tuapp.eleuthera.domain.model.TrackMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ProgressSkinViewModel @Inject constructor(
    private val repo: ProgressSkinRepository,
) : ViewModel() {

    val skins: StateFlow<List<ProgressSkin>> =
        repo.skins.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** null = diseño por defecto. */
    val activeId: StateFlow<String?> =
        repo.activeId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Diseño activo ya cargado, para la vista previa. */
    val active: StateFlow<LoadedSkin?> =
        repo.active.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun select(id: String?) = launch { repo.setActive(id) }
    fun create(name: String) { if (name.isNotBlank()) launch { repo.create(name) } }
    fun rename(id: String, name: String) { if (name.isNotBlank()) launch { repo.rename(id, name) } }
    fun delete(id: String) = launch { repo.delete(id) }
    fun setMode(id: String, mode: TrackMode) = launch { repo.setMode(id, mode) }
    fun setSpin(id: String, spin: Boolean) = launch { repo.setSpin(id, spin) }
    fun setSideColor(id: String, color: Int) = launch { repo.setSideColor(id, color) }
    fun setCenterColor(id: String, color: Int) = launch { repo.setCenterColor(id, color) }
    fun clearImage(id: String, slot: SkinSlot) = launch { repo.clearImage(id, slot) }

    fun setImage(id: String, slot: SkinSlot, uri: Uri) = launch {
        _message.value = repo.setImage(id, slot, uri).exceptionOrNull()?.message
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}