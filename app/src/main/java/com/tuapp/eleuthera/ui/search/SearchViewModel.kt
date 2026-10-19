package com.tuapp.eleuthera.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.eleuthera.data.download.DownloadScheduler
import com.tuapp.eleuthera.data.youtube.Resolved
import com.tuapp.eleuthera.data.youtube.YtDlpEngine
import com.tuapp.eleuthera.domain.model.DownloadStatus
import com.tuapp.eleuthera.domain.model.RemoteTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Buscar en Android: la lógica está en [SearchController] (común); aquí se le conecta yt-dlp
 * (YtDlpEngine) y la cola de descargas de WorkManager (DownloadScheduler).
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val engine: YtDlpEngine,
    private val scheduler: DownloadScheduler,
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
        },
    )
}