package com.tuapp.eleuthera.ui.library

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.eleuthera.data.library.MediaLibrary
import com.tuapp.eleuthera.domain.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Biblioteca en Android: le pasa a [LibraryController] (común) las canciones de MediaLibrary.
 * La búsqueda, las pestañas y los grupos los lleva el controlador.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: MediaLibrary,
) : ViewModel() {

    val controller = LibraryController()
    val state = controller.state

    private var job: Job? = null

    fun start() {
        library.refreshFolders()
        if (job != null) return
        job = viewModelScope.launch {
            library.observe().collect(controller::setTracks)
        }
    }

    /** Borra la canción del dispositivo. Devuelve un IntentSender si Android pide confirmación. */
    suspend fun delete(track: Track): IntentSender? =
        runCatching { library.delete(track) }.getOrNull()

}
