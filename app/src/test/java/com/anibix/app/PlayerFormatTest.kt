package com.anibix.app

import com.anibix.app.ui.player.episodeLabel
import com.anibix.app.ui.player.formatRemaining
import com.anibix.app.ui.player.formatTime
import com.anibix.app.ui.player.resumePositionFor
import com.anibix.app.ui.player.seekTargetMs
import com.anibix.app.ui.player.shouldShowNextCard
import com.anibix.app.ui.player.speedLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerFormatTest {
    @Test fun formatTime_menit_dan_jam() {
        assertEquals("01:05", formatTime(65_000))
        assertEquals("1:02:05", formatTime(3_725_000))
        assertEquals("00:00", formatTime(-500))
    }

    @Test fun formatRemaining_gaya_netflix() {
        assertEquals("-01:00", formatRemaining(60_000, 120_000))
        assertEquals("--:--", formatRemaining(0, 0))
    }

    @Test fun speedLabel_bulat_dan_pecahan() {
        assertEquals("1x", speedLabel(1.0f))
        assertEquals("1.25x", speedLabel(1.25f))
        assertEquals("0.5x", speedLabel(0.5f))
    }

    @Test fun episodeLabel_tidak_dobel() {
        assertEquals("Episode 3", episodeLabel(3, "Episode 3"))
        assertEquals("E3: Pertarungan", episodeLabel(3, "Pertarungan"))
    }

    @Test fun resume_hanya_untuk_episode_yang_sama() {
        assertEquals(0L, resumePositionFor(1, 2, 600_000, 1_400_000))
        assertEquals(600_000L, resumePositionFor(2, 2, 600_000, 1_400_000))
    }

    @Test fun resume_abaikan_awal_dan_hampir_tamat() {
        assertEquals(0L, resumePositionFor(1, 1, 5_000, 1_400_000))
        assertEquals(0L, resumePositionFor(1, 1, 1_380_000, 1_400_000))
    }

    @Test fun kartu_berikutnya_di_20_detik_terakhir() {
        assertTrue(shouldShowNextCard(1_385_000, 1_400_000))
        assertFalse(shouldShowNextCard(600_000, 1_400_000))
        assertFalse(shouldShowNextCard(40_000, 50_000)) // video pendek: tidak muncul
    }

    @Test fun seekTarget_dijepit() {
        assertEquals(0L, seekTargetMs(5_000, -10_000, 100_000))
        assertEquals(100_000L, seekTargetMs(95_000, 10_000, 100_000))
        assertEquals(15_000L, seekTargetMs(5_000, 10_000, 0))
    }
}
