package com.tuapp.eleuthera.domain.model

sealed interface DownloadStatus {
    data object Queued : DownloadStatus
    data class Running(val percent: Int) : DownloadStatus
    data object Done : DownloadStatus
    data class Failed(val message: String) : DownloadStatus
}
