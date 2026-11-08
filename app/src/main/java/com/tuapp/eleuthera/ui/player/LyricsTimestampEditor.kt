package com.tuapp.eleuthera.ui.player

data class LyricsPreviewLine(
    val startMs: Long?,
    val text: String,
    val cueId: Int,
)

object LyricsTimestampEditor {
    fun shift(text: String, offsetMs: Long, durationMs: Long): String {
        require(durationMs > 0) { "La duración del audio no es válida." }
        var timestampCount = 0
        val shiftedLrc = LRC_TIMESTAMP.replace(text) { match ->
            timestampCount++
            val minutes = match.groupValues[1].toLong()
            val seconds = match.groupValues[2].toLong()
            val fractionDigits = match.groupValues[4]
            val fractionMs = fractionDigits.padEnd(3, '0').toLongOrNull() ?: 0
            val shifted = ((minutes * 60 + seconds) * 1_000 + fractionMs + offsetMs)
                .coerceIn(0, durationMs)
            val shiftedMinutes = shifted / 60_000
            val shiftedSeconds = shifted / 1_000 % 60
            val shiftedFraction = shifted % 1_000
            buildString {
                append('[')
                append(shiftedMinutes.toString().padStart(2, '0'))
                append(':')
                append(shiftedSeconds.toString().padStart(2, '0'))
                if (fractionDigits.isNotEmpty() || shiftedFraction != 0L) {
                    append(match.groupValues[3].ifEmpty { "." })
                    append(
                        shiftedFraction.toString().padStart(3, '0')
                            .take(fractionDigits.length.takeIf { it > 0 } ?: 3),
                    )
                }
                append(']')
            }
        }
        val shiftedSrt = SRT_TIMESTAMP.replace(shiftedLrc) { match ->
            timestampCount++
            val hours = match.groupValues[1].toLong()
            val minutes = match.groupValues[2].toLong()
            val seconds = match.groupValues[3].toLong()
            val millis = match.groupValues[5].toLong()
            val shifted = ((hours * 60 + minutes) * 60 + seconds) * 1_000 + millis + offsetMs
            val clamped = shifted.coerceIn(0, durationMs)
            val formattedHours = clamped / 3_600_000
            val formattedMinutes = clamped / 60_000 % 60
            val formattedSeconds = clamped / 1_000 % 60
            val formattedMillis = clamped % 1_000
            "%02d:%02d:%02d%s%03d".format(
                formattedHours,
                formattedMinutes,
                formattedSeconds,
                match.groupValues[4],
                formattedMillis,
            )
        }
        if (timestampCount == 0) {
            if (offsetMs == 0L) return text
            throw IllegalArgumentException("La letra no contiene marcas de tiempo LRC o SRT que se puedan ajustar.")
        }
        return shiftedSrt
    }

    fun previewLines(text: String): List<LyricsPreviewLine> {
        var searchFrom = 0
        val lrcMatches = LRC_TIMESTAMP.findAll(text).toList()
        val srtMatches = SRT_RANGE.findAll(text).toList()

        if (lrcMatches.isNotEmpty()) {
            val lines = mutableListOf<LyricsPreviewLine>()
            var lineSearchOffset = 0
            for (rawLine in text.lines()) {
                val lineStart = text.indexOf(rawLine, lineSearchOffset).let {
                    if (it >= 0) it else lineSearchOffset
                }
                lineSearchOffset = lineStart + rawLine.length
                val trimmed = rawLine.trim()
                if (trimmed.isBlank() || LRC_METADATA.matches(trimmed)) continue

                val match = LRC_TIMESTAMP.find(trimmed)
                if (match != null) {
                    val lyric = LRC_TIMESTAMP.replace(trimmed, "").trim()
                    val minutes = match.groupValues[1].toLongOrNull() ?: continue
                    val seconds = match.groupValues[2].toLongOrNull() ?: continue
                    val fraction = match.groupValues[4].padEnd(3, '0').toLongOrNull() ?: 0
                    val startMs = (minutes * 60 + seconds) * 1_000 + fraction
                    val cueId = lineStart + match.range.first
                    lines.add(LyricsPreviewLine(startMs, lyric, cueId))
                } else {
                    lines.add(LyricsPreviewLine(null, trimmed, lineStart))
                }
            }
            if (lines.isNotEmpty()) return lines
        }

        if (srtMatches.isNotEmpty()) {
            val srtLines = text.split(Regex("""\r?\n\s*\r?\n""")).mapNotNull { block ->
                val blockStart = text.indexOf(block, searchFrom)
                if (blockStart >= 0) searchFrom = blockStart + block.length
                val lines = block.lines()
                val timestampIndex = lines.indexOfFirst { SRT_RANGE.matches(it.trim()) }
                if (timestampIndex < 0 || blockStart < 0) {
                    null
                } else {
                    val match = SRT_RANGE.matchEntire(lines[timestampIndex].trim())!!
                    val startMs = srtTimeMs(
                        match.groupValues[1],
                        match.groupValues[2],
                        match.groupValues[3],
                        match.groupValues[5],
                    )
                    LyricsPreviewLine(
                        startMs,
                        lines.drop(timestampIndex + 1).filter(String::isNotBlank).joinToString("\n"),
                        blockStart + block.indexOf(lines[timestampIndex].trim()),
                    )
                }
            }
            if (srtLines.isNotEmpty()) return srtLines
        }

        var lineOffset = 0
        return text.lines().mapNotNull { line ->
            val start = text.indexOf(line, lineOffset).let { if (it >= 0) it else lineOffset }
            lineOffset = start + line.length
            val trimmed = line.trim()
            if (trimmed.isBlank() || LRC_METADATA.matches(trimmed)) {
                null
            } else {
                LyricsPreviewLine(null, trimmed, start)
            }
        }
    }

    fun setLineStart(text: String, cueId: Int, startMs: Long, durationMs: Long): String {
        require(durationMs > 0) { "La duración del audio no es válida." }
        val targetMs = startMs.coerceIn(0, durationMs - 1)
        val lrcMatch = LRC_TIMESTAMP.findAll(text).firstOrNull { it.range.first == cueId }
        if (lrcMatch != null) {
            return text.replaceRange(
                lrcMatch.range,
                formatLrcTimestamp(targetMs, lrcMatch.groupValues[3], lrcMatch.groupValues[4]),
            )
        }

        val srtMatch = SRT_RANGE.findAll(text).firstOrNull { it.range.first == cueId }
        if (srtMatch != null) {
            val oldStartMs = srtTimeMs(
                srtMatch.groupValues[1],
                srtMatch.groupValues[2],
                srtMatch.groupValues[3],
                srtMatch.groupValues[5],
            ) ?: throw IllegalArgumentException("La marca de tiempo seleccionada no es válida.")
            val oldEndMs = srtTimeMs(
                srtMatch.groupValues[6],
                srtMatch.groupValues[7],
                srtMatch.groupValues[8],
                srtMatch.groupValues[10],
            ) ?: throw IllegalArgumentException("El final de la línea seleccionada no es válido.")
            val adjustedEndMs = (oldEndMs + targetMs - oldStartMs)
                .coerceIn(targetMs + 1, durationMs)
            val replacement = formatSrtTimestamp(
                targetMs,
                srtMatch.groupValues[4],
            ) + " --> " + formatSrtTimestamp(adjustedEndMs, srtMatch.groupValues[9])
            return text.replaceRange(srtMatch.range, replacement)
        }

        if (cueId >= 0 && cueId <= text.length) {
            val formatted = formatLrcTimestamp(targetMs, ".", "000")
            return text.replaceRange(cueId until cueId, formatted)
        }

        throw IllegalArgumentException("No se encontró la línea de letra seleccionada.")
    }

    private fun formatLrcTimestamp(ms: Long, separator: String, fractionDigits: String): String {
        val fraction = ms % 1_000
        return buildString {
            append('[')
            append((ms / 60_000).toString().padStart(2, '0'))
            append(':')
            append((ms / 1_000 % 60).toString().padStart(2, '0'))
            if (fractionDigits.isNotEmpty() || fraction != 0L) {
                val precision = maxOf(fractionDigits.length, if (fraction == 0L) 1 else 3)
                append(separator.ifEmpty { "." })
                append(fraction.toString().padStart(3, '0').padEnd(precision, '0').take(precision))
            }
            append(']')
        }
    }

    private fun formatSrtTimestamp(ms: Long, separator: String): String {
        val hours = ms / 3_600_000
        val minutes = ms / 60_000 % 60
        val seconds = ms / 1_000 % 60
        val millis = ms % 1_000
        return "%02d:%02d:%02d%s%03d".format(hours, minutes, seconds, separator, millis)
    }

    private fun srtTimeMs(hours: String, minutes: String, seconds: String, millis: String): Long? {
        val h = hours.toLongOrNull() ?: return null
        val m = minutes.toLongOrNull() ?: return null
        val s = seconds.toLongOrNull() ?: return null
        val ms = millis.toLongOrNull() ?: return null
        return ((h * 60 + m) * 60 + s) * 1_000 + ms
    }

    private val LRC_METADATA = Regex("""\[[a-zA-Z]{2,6}:.*]""")
    private val LRC_TIMESTAMP = Regex("""\[(\d{1,3}):([0-5]\d)(?:([.:])(\d{1,3}))?]""")
    private val SRT_TIMESTAMP = Regex("""(\d{2}):(\d{2}):(\d{2})([,.])(\d{3})""")
    private val SRT_RANGE = Regex(
        """(\d{2}):(\d{2}):(\d{2})([,.])(\d{3})\s+-->\s+(\d{2}):(\d{2}):(\d{2})([,.])(\d{3})""",
    )
}
