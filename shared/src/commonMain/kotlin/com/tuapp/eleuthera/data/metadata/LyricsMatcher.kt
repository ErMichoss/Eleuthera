package com.tuapp.eleuthera.data.metadata

import com.tuapp.eleuthera.data.lrclib.LrclibLyrics
import kotlin.math.abs

fun List<LrclibLyrics>.bestLyricsMatch(
    title: String,
    artist: String,
    durationMs: Long,
): LrclibLyrics? {
    val normalizedTitle = title.normalizeForLyricsMatch()
    val normalizedArtist = artist.normalizeForLyricsMatch()
        .removeSuffix("topic")
        .removeSuffix("vevo")
    if (normalizedTitle.isEmpty() || normalizedArtist.isEmpty()) return null

    return asSequence()
        .filter { lyrics ->
            val candidateTitle = (lyrics.trackName?.takeIf(String::isNotBlank) ?: lyrics.name).orEmpty()
                .normalizeForLyricsMatch()
            val candidateArtist = lyrics.artistName.orEmpty()
                .normalizeForLyricsMatch()
                .removeSuffix("topic")
                .removeSuffix("vevo")
            candidateTitle == normalizedTitle &&
                candidateArtist.isNotEmpty() &&
                (candidateArtist == normalizedArtist ||
                    candidateArtist.contains(normalizedArtist) ||
                    normalizedArtist.contains(candidateArtist))
        }
        .minByOrNull { lyrics ->
            lyrics.durationSeconds?.takeIf { durationMs > 0 }
                ?.let { abs(it * 1_000.0 - durationMs) }
                ?: Double.POSITIVE_INFINITY
        }
}

private fun String.normalizeForLyricsMatch(): String =
    lowercase().filter(Char::isLetterOrDigit)
