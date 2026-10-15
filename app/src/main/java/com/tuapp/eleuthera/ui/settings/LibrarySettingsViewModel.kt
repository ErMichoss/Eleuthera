package com.tuapp.eleuthera.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.eleuthera.data.settings.LibraryFolder
import com.tuapp.eleuthera.data.settings.LibrarySettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LibrarySettingsViewModel @Inject constructor(
    private val settings: LibrarySettings,
) : ViewModel() {

    val folders: StateFlow<List<LibraryFolder>> = settings.folders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Carpetas añadidas con la versión anterior: hay que volver a añadirlas. */
    val legacyFolders: StateFlow<List<String>> = settings.legacyFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val quickPlaylists: StateFlow<Boolean> = settings.quickPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setQuickPlaylists(enabled: Boolean) {
        viewModelScope.launch { settings.setQuickPlaylists(enabled) }
    }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun add(treeUri: Uri) {
        viewModelScope.launch {
            _message.value = if (settings.add(treeUri)) null
            else "Android no ha dado permiso para leer esa carpeta. Prueba con otra."
        }
    }

    fun remove(folder: LibraryFolder) {
        viewModelScope.launch { settings.remove(folder) }
    }

    fun removeLegacy(path: String) {
        viewModelScope.launch { settings.removeLegacy(path) }
    }
}