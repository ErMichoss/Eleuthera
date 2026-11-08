package com.tuapp.eleuthera.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LyricsTimestampEditorTest {
    @Test
    fun shiftsEveryLrcTimestampAndKeepsMetadata() {
        val source = "[ti:Song]\n[00:01.00]First\n[00:02.5]Second"

        val result = LyricsTimestampEditor.shift(source, 1_000, 10_000)

        assertEquals("[ti:Song]\n[00:02.00]First\n[00:03.5]Second", result)
    }

    @Test
    fun preservesSubsecondOffsetWhenLrcHasNoFraction() {
        val result = LyricsTimestampEditor.shift("[00:01]Line", 500, 10_000)

        assertEquals("[00:01.500]Line", result)
    }

    @Test
    fun shiftsSrtCuesAndClampsAtTrackBounds() {
        val source = "00:00:01,000 --> 00:00:02,000\nLine"

        val result = LyricsTimestampEditor.shift(source, -2_000, 10_000)

        assertEquals("00:00:00,000 --> 00:00:00,000\nLine", result)
    }

    @Test
    fun rejectsUnsynchronizedLyrics() {
        assertThrows(IllegalArgumentException::class.java) {
            LyricsTimestampEditor.shift("Plain lyric", 1_000, 10_000)
        }
    }

    @Test
    fun previewsLrcLinesWithTheirStartTimes() {
        val lines = LyricsTimestampEditor.previewLines("[00:01.25]First\n[00:02]Second")

        assertEquals(
            listOf(1_250L to "First", 2_000L to "Second"),
            lines.map { it.startMs to it.text },
        )
    }

    @Test
    fun previewsSrtLinesWithTheirStartTimes() {
        val lines = LyricsTimestampEditor.previewLines(
            "1\n00:00:01,250 --> 00:00:02,000\nFirst line\n\n" +
                "2\n00:00:02,000 --> 00:00:03,000\nSecond line",
        )

        assertEquals(
            listOf(1_250L to "First line", 2_000L to "Second line"),
            lines.map { it.startMs to it.text },
        )
    }

    @Test
    fun editsSelectedLrcLineWithoutChangingOtherLines() {
        val source = "[00:01]First\n[00:02]Second"
        val selected = LyricsTimestampEditor.previewLines(source).single { it.text == "Second" }

        val result = LyricsTimestampEditor.setLineStart(source, selected.cueId, 3_200, 10_000)

        assertEquals("[00:01]First\n[00:03.200]Second", result)
    }

    @Test
    fun editsSrtCueStartAndMovesItsEndByTheSameAmount() {
        val source = "00:00:01,250 --> 00:00:02,000\nFirst"
        val selected = LyricsTimestampEditor.previewLines(source).single()

        val result = LyricsTimestampEditor.setLineStart(source, selected.cueId, 3_000, 10_000)

        assertEquals("00:00:03,000 --> 00:00:03,750\nFirst", result)
    }
}
