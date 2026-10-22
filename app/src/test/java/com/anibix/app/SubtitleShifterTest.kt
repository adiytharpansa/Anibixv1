package com.anibix.app

import com.anibix.app.subtitles.SubtitleShifter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleShifterTest {
    private val srt = """
        1
        00:00:01,000 --> 00:00:02,500
        Halo dunia

        2
        00:00:03,000 --> 00:00:04,000
        Baris kedua
    """.trimIndent()

    @Test
    fun `delay positif menggeser semua cap waktu`() {
        val out = SubtitleShifter.shiftSrt(srt, 1500)
        assertTrue(out.contains("00:00:02,500 --> 00:00:04,000"))
        assertTrue(out.contains("00:00:04,500 --> 00:00:05,500"))
    }

    @Test
    fun `delay negatif tidak menghasilkan waktu negatif`() {
        val out = SubtitleShifter.shiftSrt(srt, -5000)
        assertTrue(out.contains("00:00:00,000 --> 00:00:00,000"))
    }

    @Test
    fun `mime berdasarkan ekstensi`() {
        assertEquals("text/x-ssa", SubtitleShifter.mimeFor("film.ass"))
        assertEquals("text/vtt", SubtitleShifter.mimeFor("film.vtt"))
        assertEquals("application/x-subrip", SubtitleShifter.mimeFor("film.srt"))
    }
}
