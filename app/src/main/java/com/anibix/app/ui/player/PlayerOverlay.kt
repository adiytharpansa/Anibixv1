package com.anibix.app.ui.player

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.anibix.app.data.Episode
import com.anibix.app.data.Title
import com.anibix.app.subtitles.SubtitleShifter
import com.anibix.app.ui.theme.FlixMuted
import com.anibix.app.ui.theme.FlixRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private data class SeekFlash(val dir: Int, val seconds: Long, val id: Long)
private data class LevelHud(val isVolume: Boolean, val fraction: Float)

private fun displayNameOf(ctx: Context, uri: Uri): String? = runCatching {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }
}.getOrNull()

/**
 * Lapisan kontrol gaya Netflix di atas PlayerView: gradien atas/bawah, tombol
 * tengah (-10 / putar / +10), seek bar merah, aksi bawah (kecepatan, kunci,
 * episode, audio & subtitle, berikutnya, PiP), panel samping, kartu episode
 * berikutnya, gestur (ketuk-ganda berantai, kecerahan, volume, cubit untuk
 * isi layar) dan kunci layar.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun PlayerOverlay(
    player: ExoPlayer,
    playerView: PlayerView?,
    state: PlaybackSnapshot,
    title: Title?,
    episode: Episode,
    tracks: Tracks?,
    nextEpisode: Episode?,
    hasNext: Boolean,
    isEpisodeAvailable: (Episode) -> Boolean,
    downloadRateKb: Long,
    inPip: Boolean,
    initialHud: String?,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSelectEpisode: (Int) -> Unit,
    onEnterPip: () -> Unit,
) {
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findActivity() }
    val audioManager = remember(ctx) { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val playlist = remember(title) { title?.playlist().orEmpty() }
    val currentPlayerView by rememberUpdatedState(playerView)

    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var unlockHint by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(PlayerPanel.None) }
    var scrubbing by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    var hud by remember { mutableStateOf<String?>(null) }
    var seekFlash by remember { mutableStateOf<SeekFlash?>(null) }
    var level by remember { mutableStateOf<LevelHud?>(null) }
    var nextDismissed by remember { mutableStateOf(false) }
    var fillScreen by remember { mutableStateOf(false) }
    var lastSeekAt by remember { mutableLongStateOf(0L) }
    var lastSeekDir by remember { mutableIntStateOf(0) }
    var seekAccumSec by remember { mutableLongStateOf(0L) }

    // Subtitle sideload + delay.
    var externalSubName by remember { mutableStateOf<String?>(null) }
    var subDelayMs by remember { mutableLongStateOf(0L) }
    var pendingSubUri by remember { mutableStateOf<Uri?>(null) }
    val subPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { pendingSubUri = uri; subDelayMs = 0L }
    }

    // Kontrol hilang otomatis 4 dtk saat memutar (tidak saat menggeser / panel terbuka).
    LaunchedEffect(controlsVisible, state.isPlaying, scrubbing, panel, interaction) {
        if (controlsVisible && state.isPlaying && !scrubbing && panel == PlayerPanel.None) {
            delay(4000)
            controlsVisible = false
        }
    }
    LaunchedEffect(state.ended) { if (state.ended) controlsVisible = true }
    LaunchedEffect(initialHud) { if (initialHud != null) hud = initialHud }
    LaunchedEffect(hud) { if (hud != null) { delay(1600); hud = null } }
    LaunchedEffect(seekFlash?.id) { if (seekFlash != null) { delay(700); seekFlash = null } }
    LaunchedEffect(level) { if (level != null) { delay(900); level = null } }
    LaunchedEffect(unlockHint) { if (unlockHint) { delay(2500); unlockHint = false } }

    // Subtitle sideload: debounce supaya geser delay tidak memuat ulang berulang kali.
    LaunchedEffect(pendingSubUri, subDelayMs) {
        val uri = pendingSubUri ?: return@LaunchedEffect
        delay(350)
        val currentUri = player.currentMediaItem?.localConfiguration?.uri ?: return@LaunchedEffect
        val name = withContext(Dispatchers.IO) { displayNameOf(ctx, uri) }
            ?: uri.lastPathSegment ?: "subtitle.srt"
        val subUri: Uri = withContext(Dispatchers.IO) {
            runCatching {
                if (name.endsWith(".srt", true) && subDelayMs != 0L) {
                    val raw = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@runCatching uri
                    val f = File(ctx.cacheDir, "subs/sub_shifted.srt").apply { parentFile?.mkdirs() }
                    f.writeText(SubtitleShifter.shiftSrt(raw.decodeToString(), subDelayMs))
                    Uri.fromFile(f)
                } else uri
            }.getOrDefault(uri)
        }
        externalSubName = name
        val keepPos = player.currentPosition
        val keepPlay = player.playWhenReady
        val item = MediaItem.Builder()
            .setUri(currentUri)
            .setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(subUri)
                        .setMimeType(SubtitleShifter.mimeFor(name))
                        .setLanguage("id")
                        .setLabel(name)
                        .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                        .build()
                )
            )
            .build()
        player.setMediaItem(item, keepPos)
        player.prepare()
        player.playWhenReady = keepPlay
    }

    // Kartu "Episode berikutnya" + hitung mundur otomatis saat video tamat.
    val showNextCard = hasNext && !nextDismissed &&
        (state.ended || shouldShowNextCard(state.positionMs, state.durationMs))
    val nextFill = remember { Animatable(0f) }
    LaunchedEffect(state.ended, hasNext, nextDismissed) {
        if (state.ended && hasNext && !nextDismissed) {
            nextFill.snapTo(0f)
            nextFill.animateTo(1f, tween(5000, easing = LinearEasing))
            onNext()
        } else {
            nextFill.snapTo(0f)
        }
    }

    fun seekBy(dir: Int) {
        val now = SystemClock.uptimeMillis()
        if (now - lastSeekAt > 900L || dir != lastSeekDir) seekAccumSec = 0L
        seekAccumSec += 10L
        lastSeekAt = now
        lastSeekDir = dir
        player.seekTo(seekTargetMs(player.currentPosition, dir * 10_000L, player.duration))
        seekFlash = SeekFlash(dir, seekAccumSec, now)
    }

    fun togglePlay() {
        interaction++
        when {
            state.ended -> { player.seekTo(0); player.play() }
            player.isPlaying -> player.pause()
            else -> player.play()
        }
    }

    fun setFill(fill: Boolean) {
        if (fill == fillScreen) return
        fillScreen = fill
        currentPlayerView?.setResizeMode(
            if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
        )
        hud = if (fill) "Isi layar" else "Sesuai layar"
    }

    if (inPip) return

    Box(Modifier.fillMaxSize()) {
        // ---- Lapisan gestur ----
        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    // Cubit: lebarkan = isi layar, rapatkan = sesuai layar.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var zoom = 1f
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.size > 1) zoom *= event.calculateZoom()
                        } while (event.changes.any { it.pressed })
                        if (zoom > 1.1f) setFill(true) else if (zoom < 0.9f) setFill(false)
                    }
                }
                .pointerInput(locked) {
                    detectTapGestures(
                        onTap = { offset ->
                            when {
                                panel != PlayerPanel.None -> panel = PlayerPanel.None
                                locked -> unlockHint = true
                                SystemClock.uptimeMillis() - lastSeekAt < 700L && lastSeekDir != 0 ->
                                    seekBy(if (offset.x < size.width / 2f) -1 else 1)
                                else -> controlsVisible = !controlsVisible
                            }
                        },
                        onDoubleTap = { offset ->
                            if (!locked && panel == PlayerPanel.None) {
                                seekBy(if (offset.x < size.width / 2f) -1 else 1)
                            }
                        }
                    )
                }
                .pointerInput(locked) {
                    if (locked) return@pointerInput
                    var leftSide = true
                    var value = 0.5f
                    detectVerticalDragGestures(
                        onDragStart = { o ->
                            leftSide = o.x < size.width / 2f
                            value = if (leftSide) currentBrightness(activity) else volumeFraction(audioManager)
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            value = (value - dragAmount / (size.height * 0.8f))
                                .coerceIn(if (leftSide) 0.02f else 0f, 1f)
                            if (leftSide) applyBrightness(activity, value) else applyVolume(audioManager, value)
                            level = LevelHud(!leftSide, value)
                        }
                    )
                }
        )

        // ---- Indikator ketuk-ganda ----
        seekFlash?.let { f ->
            Column(
                Modifier.align(if (f.dir < 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 72.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    if (f.dir < 0) Icons.Default.FastRewind else Icons.Default.FastForward,
                    null, tint = Color.White, modifier = Modifier.size(36.dp)
                )
                Text("${f.seconds} dtk", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }

        // ---- Indikator kecerahan / volume ----
        level?.let { l ->
            Column(
                Modifier.align(if (l.isVolume) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(vertical = 14.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    when {
                        !l.isVolume -> Icons.Default.BrightnessMedium
                        l.fraction <= 0f -> Icons.Default.VolumeOff
                        else -> Icons.Default.VolumeUp
                    },
                    null, tint = Color.White, modifier = Modifier.size(24.dp)
                )
                Box(
                    Modifier.width(4.dp).height(110.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                ) {
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .fillMaxHeight(l.fraction.coerceIn(0f, 1f))
                            .background(Color.White)
                    )
                }
                Text("${(l.fraction * 100).toInt()}", color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
        }

        // ---- Spinner buffering (selalu tampil, juga saat kontrol tersembunyi) ----
        if (state.isBuffering && !state.ended) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = FlixRed, strokeWidth = 4.dp, modifier = Modifier.size(56.dp))
                if (downloadRateKb > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("$downloadRateKb KB/s", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // ---- Kontrol utama ----
        AnimatedVisibility(
            visible = controlsVisible && !locked && panel == PlayerPanel.None,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(250)),
        ) {
            Box(Modifier.fillMaxSize()) {
                // Bar atas
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                        .safeDrawingPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = Color.White)
                    }
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(
                            title?.name ?: "", color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (playlist.size > 1) {
                            Text(
                                episodeLabel(episode.number, episode.name),
                                color = FlixMuted, style = MaterialTheme.typography.bodySmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Tengah: -10 / putar / +10
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { interaction++; player.seekTo(seekTargetMs(player.currentPosition, -10_000L, player.duration)) },
                        modifier = Modifier.size(56.dp)
                    ) { Icon(Icons.Default.Replay10, "Mundur 10 detik", tint = Color.White, modifier = Modifier.size(40.dp)) }

                    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                        if (!state.isBuffering || state.ended) {
                            IconButton(onClick = { togglePlay() }, modifier = Modifier.size(72.dp)) {
                                Icon(
                                    when {
                                        state.ended -> Icons.Default.Replay
                                        state.isPlaying -> Icons.Default.Pause
                                        else -> Icons.Default.PlayArrow
                                    },
                                    contentDescription = when {
                                        state.ended -> "Putar ulang"
                                        state.isPlaying -> "Jeda"
                                        else -> "Putar"
                                    },
                                    tint = Color.White, modifier = Modifier.size(60.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { interaction++; player.seekTo(seekTargetMs(player.currentPosition, 10_000L, player.duration)) },
                        modifier = Modifier.size(56.dp)
                    ) { Icon(Icons.Default.Forward10, "Maju 10 detik", tint = Color.White, modifier = Modifier.size(40.dp)) }
                }

                // Bawah: seek bar + aksi
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                        .safeDrawingPadding()
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp, bottom = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlayerSeekBar(
                            positionMs = state.positionMs,
                            bufferedMs = state.bufferedMs,
                            durationMs = state.durationMs,
                            onScrubbingChanged = { scrubbing = it },
                            onSeek = { ms -> interaction++; player.seekTo(ms) },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            formatRemaining(state.positionMs, state.durationMs),
                            color = Color.White, style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ActionButton(Icons.Default.Speed, speedLabel(state.speed), "Kecepatan putar") {
                                panel = PlayerPanel.Speed
                            }
                            ActionButton(Icons.Default.Lock, "Kunci", "Kunci layar") {
                                locked = true; controlsVisible = false; hud = "Layar terkunci"
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (playlist.size > 1) {
                                ActionButton(Icons.Default.VideoLibrary, "Episode", "Daftar episode") {
                                    panel = PlayerPanel.Episodes
                                }
                            }
                            ActionButton(Icons.Default.Subtitles, "Audio & Subtitle", "Audio dan subtitle") {
                                panel = PlayerPanel.AudioSubtitles
                            }
                            if (hasNext) {
                                ActionButton(Icons.Default.SkipNext, "Berikutnya", "Episode berikutnya") { onNext() }
                            }
                            ActionButton(Icons.Default.PictureInPictureAlt, null, "Picture-in-Picture") { onEnterPip() }
                        }
                    }
                }
            }
        }

        // ---- Kartu episode berikutnya ----
        AnimatedVisibility(
            visible = showNextCard && panel == PlayerPanel.None,
            modifier = Modifier.align(Alignment.BottomEnd),
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(200)),
        ) {
            Column(
                Modifier.safeDrawingPadding()
                    .padding(
                        end = 16.dp,
                        bottom = if (controlsVisible && !locked) 96.dp else 16.dp
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xE6141414))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Episode berikutnya", color = FlixMuted, style = MaterialTheme.typography.bodySmall)
                if (nextEpisode != null) {
                    Text(
                        episodeLabel(nextEpisode.number, nextEpisode.name),
                        color = Color.White, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.height(44.dp).width(150.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                            .clickable { onNext() },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.align(Alignment.CenterStart).fillMaxHeight()
                                .fillMaxWidth(nextFill.value.coerceIn(0f, 1f))
                                .background(Color(0xFFBDBDBD))
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.Black)
                            Text("Putar", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        "Tutup", color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp))
                            .clickable { nextDismissed = true }
                            .padding(horizontal = 10.dp, vertical = 12.dp)
                    )
                }
            }
        }

        // ---- Petunjuk buka kunci ----
        if (locked) {
            AnimatedVisibility(
                visible = unlockHint,
                modifier = Modifier.align(Alignment.Center),
                enter = fadeIn(tween(150)), exit = fadeOut(tween(250)),
            ) {
                Row(
                    Modifier.clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .clickable { locked = false; unlockHint = false; controlsVisible = true }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.LockOpen, null, tint = Color.White)
                    Text("Ketuk untuk membuka kunci", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // ---- Pesan singkat (HUD) ----
        hud?.let { msg ->
            Text(
                msg, color = Color.White, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // ---- Panel samping ----
        var lastPanel by remember { mutableStateOf(PlayerPanel.Speed) }
        val contentPanel = if (panel != PlayerPanel.None) panel else lastPanel
        SideEffect { if (panel != PlayerPanel.None) lastPanel = panel }
        AnimatedVisibility(
            visible = panel != PlayerPanel.None,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(tween(220)) { it } + fadeIn(tween(220)),
            exit = slideOutHorizontally(tween(180)) { it } + fadeOut(tween(180)),
        ) {
            PlayerSidePanel(
                panel = contentPanel,
                player = player,
                tracks = tracks,
                speed = state.speed,
                playlist = playlist,
                currentEpisode = episode.number,
                isAvailable = isEpisodeAvailable,
                externalSubName = externalSubName,
                subDelayMs = subDelayMs,
                onSubDelayChange = { subDelayMs = it.coerceIn(-10_000L, 10_000L) },
                onPickSubtitle = { panel = PlayerPanel.None; subPicker.launch("*/*") },
                onSelectEpisode = { n -> panel = PlayerPanel.None; onSelectEpisode(n) },
                onClose = { panel = PlayerPanel.None },
            )
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String?, description: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(22.dp))
        if (label != null) {
            Text(label, color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
    }
}
