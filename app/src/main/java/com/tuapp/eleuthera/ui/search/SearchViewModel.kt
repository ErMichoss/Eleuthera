package com.tuapp.eleuthera.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.eleuthera.data.db.DownloadDao
import com.tuapp.eleuthera.data.download.DownloadScheduler
import com.tuapp.eleuthera.data.library.MediaLibrary
import com.tuapp.eleuthera.data.youtube.Resolved
import com.tuapp.eleuthera.data.youtube.YtDlpEngine
import com.tuapp.eleuthera.domain.model.DownloadStatus
import com.tuapp.eleuthera.domain.model.RemoteTrack
import com.tuapp.eleuthera.domain.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Buscar en Android: la lógica está en [SearchController] (común); aquí se le conecta yt-dlp
 * (YtDlpEngine) y la cola de descargas de WorkManager (DownloadScheduler).
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val engine: YtDlpEngine,
    private val scheduler: DownloadScheduler,
    private val dao: DownloadDao,
    private val library: MediaLibrary,
) : ViewModel() {

    val controller = SearchController(
        viewModelScope,
        object : SearchBackend {
            override suspend fun resolve(target: String): Resolved = engine.resolve(target)
            override fun downloads(): Flow<Map<String, DownloadStatus>> = scheduler.observe()
            override suspend fun enqueue(track: RemoteTrack, playlist: String?, index: Int) =
                scheduler.enqueue(track, playlist, index)
            override fun cancel(videoId: String) {
                scheduler.cancel(videoId)
            }

            // MediaStore puede tardar un momento en ver el archivo recién publicado.
            override suspend fun trackFor(videoId: String, waitMs: Long): Track? {
                val trackId = dao.trackIdFor(videoId) ?: return null
                return withTimeoutOrNull(waitMs.coerceAtLeast(3_000)) {
                    library.observe()
                        .map { all -> all.firstOrNull { it.id == trackId } }
                        .filterNotNull()
                        .first()
                }
            }
        },
    )
}