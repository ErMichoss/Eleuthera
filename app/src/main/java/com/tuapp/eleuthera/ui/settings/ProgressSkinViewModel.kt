package com.tuapp.eleuthera.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.eleuthera.data.settings.ProgressSkinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Barra de progreso en Android: la lógica está en [com.tuapp.eleuthera.data.settings.ProgressSkinController] (común). */
@HiltViewModel
class ProgressSkinViewModel @Inject constructor(repo: ProgressSkinRepository) : ViewModel() {
    val controller = ProgressSkinController(viewModelScope, repo)

    /** Para el resumen del menú de Ajustes. */
    val skins = controller.skins
    val activeId = controller.activeId
}