package com.tuapp.eleuthera.data.lrclib

import com.tuapp.eleuthera.data.metadata.bestLyricsMatch
import kotlin.math.abs

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

    /**
     * Searches a few progressively broader text queries, then ranks and deduplicates results.
     * This is intentionally separate from automatic matching: the user confirms the candidate.
     */
    suspend fun searchCandidates(
        trackName: String,
        artistName: String,
        durationMs: Long,
    ): List<LrclibLyrics> {
        val cleanTitle = trackName
            .replace(FEATURED_ARTISTS, "")
            .replace(VERSION_SUFFIX, "")
            .trim()
            .ifBlank { trackName.trim() }
        val queries = buildList {
            if (trackName.isNotBlank() && artistName.isNotBlank()) add("$trackName $artistName".trim())
            if (cleanTitle.isNotBlank() && artistName.isNotBlank()) add("$cleanTitle $artistName".trim())
            if (cleanTitle.isNotBlank()) add(cleanTitle)
        }.distinct()

        val results = linkedMapOf<String, LrclibLyrics>()
        for (query in queries) {
            service.searchLyrics(query, refreshCache = true).forEach { lyrics ->
                if (!lyrics.syncedLyrics.isNullOrBlank() || !lyrics.plainLyrics.isNullOrBlank()) {
                    val key = lyrics.id.takeIf { it > 0 }?.toString()
                        ?: listOf(lyrics.trackName, lyrics.artistName, lyrics.albumName)
                            .joinToString("|") { it.orEmpty().trim().lowercase() }
                    results.putIfAbsent(key, lyrics)
                }
            }
            if (results.size >= MAX_MANUAL_CANDIDATES) break
        }

        val titleWords = cleanTitle.words()
        val artistWords = artistName.words()
        return results.values
            .sortedByDescending { candidate ->
                candidateScore(candidate, titleWords, artistWords, durationMs)
            }
            .take(MAX_MANUAL_CANDIDATES)
    }

    private fun candidateScore(
        candidate: LrclibLyrics,
        titleWords: Set<String>,
        artistWords: Set<String>,
        durationMs: Long,
    ): Double {
        val candidateTitle = (candidate.trackName ?: candidate.name).orEmpty().words()
        val candidateArtist = candidate.artistName.orEmpty().words()
        val titleOverlap = overlap(titleWords, candidateTitle)
        val artistOverlap = overlap(artistWords, candidateArtist)
        val durationPenalty = candidate.durationSeconds
            ?.takeIf { durationMs > 0 }
            ?.let { abs(it * 1_000.0 - durationMs) / 1_000.0 }
            ?: 0.0
        return titleOverlap * 4 + artistOverlap * 3 - durationPenalty / 30
    }

    private fun overlap(expected: Set<String>, candidate: Set<String>): Double =
        if (expected.isEmpty()) 0.0 else expected.intersect(candidate).size.toDouble() / expected.size

    private fun String.words(): Set<String> =
        lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter(String::isNotBlank).toSet()

    private companion object {
        const val MAX_MANUAL_CANDIDATES = 10
        val FEATURED_ARTISTS = Regex("""\s*[\[(]?\s*(feat\.?|ft\.?|featuring)\b.*$""", RegexOption.IGNORE_CASE)
        val VERSION_SUFFIX = Regex(
            """\s*(?:\([^)]*(?:remaster(?:ed)?|radio edit|live|acoustic|version)[^)]*\)|\[[^\]]*(?:remaster(?:ed)?|radio edit|live|acoustic|version)[^\]]*\])\s*$""",
            RegexOption.IGNORE_CASE,
        )
    }
}
