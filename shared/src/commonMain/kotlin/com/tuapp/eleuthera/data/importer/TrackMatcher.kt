package com.tuapp.eleuthera.data.importer

import com.tuapp.eleuthera.domain.model.ImportedTrack
import com.tuapp.eleuthera.domain.model.RemoteTrack
import java.text.Normalizer
import kotlin.math.abs

/**
 * Elige, entre los resultados de YouTube, el que más probablemente sea la canción original:
 * duración parecida, canal oficial ("Artista - Topic") y sin versiones en directo, covers, etc.
 */
object TrackMatcher {

    /** Palabras que delatan otra versión; solo penalizan si no están en el título original. */
    private val unwanted = listOf(
        "live", "en vivo", "en directo", "cover", "karaoke", "remix", "sped up", "slowed",
        "nightcore", "8d", "instrumental", "reaction", "tutorial", "lyrics video", "acoustic",
    )

    fun best(target: ImportedTrack, candidates: List<RemoteTrack>): RemoteTrack? {
        if (candidates.isEmpty()) return null
        val scored = candidates.map { it to score(target, it) }
        val (best, points) = scored.maxBy { it.second }
        // Si la duración era conocida y ninguna se le parece, mejor no descargar algo erróneo.
        val known = target.durationSec
        if (known != null && best.durationSec > 0 && abs(best.durationSec - known) > 90) return null
        return best.takeIf { points > -4 }
    }

    private fun score(target: ImportedTrack, c: RemoteTrack): Int {
        var s = 0
        val title = normalize(c.title)
        val author = normalize(c.author)
        val wantedTitle = normalize(target.title)
        val mainArtist = normalize(target.artist.substringBefore(','))

        target.durationSec?.let { d ->
            if (c.durationSec > 0) {
                val diff = abs(c.durationSec - d)
                s += when {
                    diff <= 3 -> 4
                    diff <= 10 -> 2
                    diff <= 30 -> 0
                    else -> -4
                }
            }
        }
        if (author.endsWith(" - topic")) s += 3                    // pistas oficiales de YouTube Music
        if (mainArtist.isNotEmpty() && mainArtist in author) s += 2
        if (mainArtist.isNotEmpty() && mainArtist in title) s += 1
        if (wantedTitle in title) s += 2
        unwanted.forEach { word ->
            if (word in title && word !in wantedTitle) s -= 3
        }
        return s
    }

    private fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
}