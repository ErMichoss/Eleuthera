package com.tuapp.eleuthera.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.eleuthera.data.settings.LibrarySettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LibrarySettingsViewModel @Inject constructor(
    private val settings: LibrarySettings,
) : ViewModel() {

    val folders: StateFlow<List<String>> = settings.folders
        .map { it.sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun add(treeUri: Uri) {
        viewModelScope.launch {
            _message.value = if (settings.add(treeUri) == null) {
                "Solo se admiten carpetas del almacenamiento interno, no de la tarjeta SD."
            } else null
        }
    }

    fun remove(path: String) {
        viewModelScope.launch { settings.remove(path) }
    }
}
