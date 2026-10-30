package com.tuapp.eleuthera.data.lrclib

import com.tuapp.eleuthera.data.metadata.bestLyricsMatch

/** Repository boundary for LRCLIB lookups; the service owns the HTTP/REST and cache details. */
class LyricsRepository(
    private val service: LyricsSearchService,
) {
    suspend fun findBestMatch(
        trackName: String,
        artistName: String,
        durationMs: Long,
        refreshCache: Boolean = false,
    ): LrclibLyrics? = service.searchLyricsByMetadata(
        trackName = trackName,
        artistName = artistName,
        refreshCache = refreshCache,
    ).bestLyricsMatch(trackName, artistName, durationMs)
}
