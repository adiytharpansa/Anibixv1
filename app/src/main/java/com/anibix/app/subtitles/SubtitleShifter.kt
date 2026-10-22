package com.anibix.app.subtitles

/**
 * Geser semua cap waktu SRT sebesar [offsetMs] (untuk penyesuaian sinkronisasi
 * subtitle sideload). Baris waktu ASS/SSA tidak diubah (passthrough) — untuk
 * berkas .ass, pemutar memakai timing asli.
 */
object SubtitleShifter {
    private val timeLine = Regex("""(\d{2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2})[,.](\d{3})""")

    fun shiftSrt(content: String, offsetMs: Long): String {
        if (offsetMs == 0L) return content
        return timeLine.replace(content) { m ->
            val start = toMs(m.groupValues, 1) + offsetMs
            val end = toMs(m.groupValues, 5) + offsetMs
            "${format(start)} --> ${format(end)}"
        }
    }

    private fun toMs(g: List<String>, i: Int): Long =
        g[i].toLong() * 3_600_000 + g[i + 1].toLong() * 60_000 + g[i + 2].toLong() * 1_000 + g[i + 3].toLong()

    private fun format(ms: Long): String {
        val v = ms.coerceAtLeast(0)
        return "%02d:%02d:%02d,%03d".format(v / 3_600_000, (v / 60_000) % 60, (v / 1_000) % 60, v % 1_000)
    }

    /** MIME untuk Media3 berdasarkan ekstensi berkas subtitle. */
    fun mimeFor(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "ass", "ssa" -> "text/x-ssa"
        "vtt" -> "text/vtt"
        else -> "application/x-subrip"
    }
}
