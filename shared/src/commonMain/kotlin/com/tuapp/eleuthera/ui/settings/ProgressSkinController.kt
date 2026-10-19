package com.tuapp.eleuthera.ui.settings

import com.tuapp.eleuthera.data.settings.LoadedSkin
import com.tuapp.eleuthera.data.settings.ProgressSkinStore
import com.tuapp.eleuthera.domain.model.ProgressSkin
import com.tuapp.eleuthera.domain.model.SkinSlot
import com.tuapp.eleuthera.domain.model.TrackMode
import com.tuapp.eleuthera.platform.PlatformUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Ajustes > Barra de progreso. Igual en Android y escritorio. */
class ProgressSkinController(private val scope: CoroutineScope, private val store: ProgressSkinStore) {

    val skins: StateFlow<List<ProgressSkin>> =
        store.skins.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** null = diseño por defecto. */
    val activeId: StateFlow<String?> =
        store.activeId.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /** Diseño activo ya cargado, para la vista previa. */
    val active: StateFlow<LoadedSkin?> =
        store.active.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun select(id: String?) = launch { store.setActive(id) }
    fun create(name: String) { if (name.isNotBlank()) launch { store.create(name) } }
    fun rename(id: String, name: String) { if (name.isNotBlank()) launch { store.rename(id, name) } }
    fun delete(id: String) = launch { store.delete(id) }
    fun setMode(id: String, mode: TrackMode) = launch { store.setMode(id, mode) }
    fun setSpin(id: String, spin: Boolean) = launch { store.setSpin(id, spin) }
    fun setSideColor(id: String, color: Int) = launch { store.setSideColor(id, color) }
    fun setCenterColor(id: String, color: Int) = launch { store.setCenterColor(id, color) }
    fun clearImage(id: String, slot: SkinSlot) = launch { store.clearImage(id, slot) }

    fun setImage(id: String, slot: SkinSlot, image: PlatformUri) = launch {
        _message.value = store.setImage(id, slot, image).exceptionOrNull()?.message
    }

    private fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }
}