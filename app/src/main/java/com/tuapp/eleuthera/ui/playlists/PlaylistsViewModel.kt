package com.tuapp.eleuthera.ui.playlists

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.eleuthera.data.playlist.PlaylistRepository
import com.tuapp.eleuthera.domain.model.Playlist
import com.tuapp.eleuthera.domain.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaylistsViewModel @Inject constructor(private val repo: PlaylistRepository) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> =
        repo.playlists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _openId = MutableStateFlow<Long?>(null)
    /** Playlist abierta, o null si se ve la cuadrícula. */
    val openId: StateFlow<Long?> = _openId.asStateFlow()

    val openTracks: StateFlow<List<Track>> = _openId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.tracks(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { runCatching { repo.importDownloadedFoldersOnce() } }
    }

    fun open(playlistId: Long) { _openId.value = playlistId }
    fun close() { _openId.value = null }

    fun create(name: String, firstTrack: Track? = null) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = repo.create(name)
            firstTrack?.let { repo.add(id, it.id) }
        }
    }

    fun delete(playlistId: Long) {
        if (_openId.value == playlistId) close()
        viewModelScope.launch { repo.delete(playlistId) }
    }

    fun add(playlistId: Long, track: Track) = viewModelScope.launch { repo.add(playlistId, track.id) }

    fun rename(playlistId: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.rename(playlistId, name) }
    }

    fun setCover(playlistId: Long, image: Uri) {
        viewModelScope.launch { runCatching { repo.setCover(playlistId, image) } }
    }

    fun clearCover(playlistId: Long) {
        viewModelScope.launch { repo.clearCover(playlistId) }
    }

    fun remove(track: Track) = withOpen { repo.remove(it, track.id) }
    fun reorder(orderedTrackIds: List<Long>) = withOpen { repo.reorder(it, orderedTrackIds) }

    private fun withOpen(block: suspend (Long) -> Unit) {
        val id = _openId.value ?: return
        viewModelScope.launch { block(id) }
    }
}
