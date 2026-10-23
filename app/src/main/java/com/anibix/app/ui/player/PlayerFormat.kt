package com.anibix.app.ui.player

import java.util.Locale

/**
 * Fungsi murni (tanpa dependensi Android) untuk pemutar — mudah diuji unit.
 */

val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

/** Foto keadaan pemutaran yang dikirim ke lapisan UI. */
data class PlaybackSnapshot(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val ended: Boolean = false,
    val positionMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
)

enum class PlayerPanel { None, Episodes, AudioSubtitles, Speed }

/** 65 dtk → "01:05"; 1 jam 2 mnt 5 dtk → "1:02:05". */
fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%02d:%02d", m, s)
}

/** Sisa waktu gaya Netflix: "-12:34". Durasi belum diketahui → "--:--". */
fun formatRemaining(positionMs: Long, durationMs: Long): String =
    if (durationMs <= 0) "--:--" else "-" + formatTime(durationMs - positionMs)

fun speedLabel(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"

fun episodeLabel(number: Int, name: String): String =
    if (name.isBlank() || name.equals("Episode $number", ignoreCase = true)) "Episode $number"
    else "E$number: $name"

/**
 * Posisi mulai saat membuka episode. Riwayat tonton disimpan satu baris per
 * judul, jadi posisi dari episode LAIN tidak boleh dipakai. Posisi terlalu
 * awal (<10 dtk) atau hampir tamat (<30 dtk tersisa) → mulai dari awal.
 */
fun resumePositionFor(
    savedEpisode: Int,
    currentEpisode: Int,
    savedPositionMs: Long,
    savedDurationMs: Long,
): Long {
    if (savedEpisode != currentEpisode) return 0L
    if (savedPositionMs < 10_000L) return 0L
    if (savedDurationMs > 0 && savedDurationMs - savedPositionMs < 30_000L) return 0L
    return savedPositionMs
}

/** Kartu "Episode berikutnya" muncul di 20 detik terakhir (kredit akhir). */
fun shouldShowNextCard(positionMs: Long, durationMs: Long): Boolean =
    durationMs > 60_000L && durationMs - positionMs <= 20_000L

/** Target seek relatif, dijepit ke [0, durasi] bila durasi diketahui. */
fun seekTargetMs(currentMs: Long, deltaMs: Long, durationMs: Long): Long {
    val target = (currentMs + deltaMs).coerceAtLeast(0L)
    return if (durationMs > 0) target.coerceAtMost(durationMs) else target
}
