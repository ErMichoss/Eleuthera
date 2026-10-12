package com.tuapp.eleuthera.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.eleuthera.data.settings.ThemeRepository
import com.tuapp.eleuthera.domain.model.ThemeConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado global del tema: lo lee MainActivity y lo modifica la pantalla de Ajustes. */
@HiltViewModel
class ThemeViewModel @Inject constructor(private val repo: ThemeRepository) : ViewModel() {

    val config: StateFlow<ThemeConfig> =
        repo.config.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeConfig())

    fun update(transform: (ThemeConfig) -> ThemeConfig) {
        viewModelScope.launch { repo.save(transform(config.value)) }
    }

    fun reset() = update { ThemeConfig() }
}
