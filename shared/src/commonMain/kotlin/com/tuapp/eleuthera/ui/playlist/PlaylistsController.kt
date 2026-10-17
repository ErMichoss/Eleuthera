package com.tuapp.eleuthera.ui.playlists

import com.tuapp.eleuthera.data.playlist.PlaylistStore
import com.tuapp.eleuthera.domain.model.Playlist
import com.tuapp.eleuthera.domain.model.Track
import com.tuapp.eleuthera.platform.PlatformUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Lo que se ve en Playlists (cuadrícula y detalle) y el diálogo "Añadir a playlist". Igual
 * en las dos plataformas; [scope] es el del ViewModel en Android y el de la app en escritorio.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistsController(private val scope: CoroutineScope, private val store: PlaylistStore) {

    val playlists: StateFlow<List<Playlist>> =
        store.playlists().stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _openId = MutableStateFlow<Long?>(null)

    /** Playlist abierta, o null si se ve la cuadrícula. */
    val openId: StateFlow<Long?> = _openId.asStateFlow()

    val openTracks: StateFlow<List<Track>> = _openId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else store.tracks(id) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Canciones de una playlist en su orden, para mandarla a la cola sin abrirla. */
    suspend fun tracksOf(playlistId: Long): List<Track> = store.tracks(playlistId).first()

    fun open(playlistId: Long) { _openId.value = playlistId }
    fun close() { _openId.value = null }

    /** Crea una playlist, opcionalmente ya con [tracks] dentro. */
    fun create(name: String, tracks: List<Track> = emptyList()) {
        if (name.isBlank()) return
        scope.launch {
            val id = store.create(name)
            if (tracks.isNotEmpty()) store.addAll(id, tracks.map { it.id })
        }
    }

    /** Añade varias canciones a una playlist. Devuelve cuántas eran nuevas (las repetidas se saltan). */
    suspend fun addAll(playlistId: Long, tracks: List<Track>): Int =
        store.addAll(playlistId, tracks.map { it.id })

    fun delete(playlistId: Long) {
        if (_openId.value == playlistId) close()
        scope.launch { store.delete(playlistId) }
    }

    fun add(playlistId: Long, track: Track) = scope.launch { store.add(playlistId, track.id) }

    fun rename(playlistId: Long, name: String) {
        if (name.isBlank()) return
        scope.launch { store.rename(playlistId, name) }
    }

    fun setCover(playlistId: Long, image: PlatformUri) {
        scope.launch { runCatching { store.setCover(playlistId, image) } }
    }

    fun clearCover(playlistId: Long) {
        scope.launch { store.clearCover(playlistId) }
    }

    fun remove(track: Track) = withOpen { store.remove(it, track.id) }
    fun removeAll(tracks: List<Track>) = withOpen { store.removeAll(it, tracks.map(Track::id)) }
    fun reorder(orderedTrackIds: List<Long>) = withOpen { store.reorder(it, orderedTrackIds) }

    private fun withOpen(block: suspend (Long) -> Unit) {
        val id = _openId.value ?: return
        scope.launch { block(id) }
    }
}