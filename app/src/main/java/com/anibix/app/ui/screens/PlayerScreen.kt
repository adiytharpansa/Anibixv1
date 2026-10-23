package com.anibix.app.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.anibix.app.data.Episode
import com.anibix.app.data.Title
import com.anibix.app.subtitles.SubtitleShifter
import com.anibix.app.ui.MainViewModel
import com.anibix.app.ui.components.GlassIconButton
import com.anibix.app.ui.theme.FlixMuted
import com.anibix.app.ui.theme.FlixRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600; val m = (totalSec % 3600) / 60; val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/**
 * Pemutar Media3 produksi: kontrol kustom (play/pause, seek, kecepatan,
 * trek audio/subtitle, PiP), gestur ketuk-ganda ±10 dtk, geser vertikal
 * untuk kecerahan (kiri) & volume (kanan), subtitle sideload (SRT/ASS/VTT)
 * dengan gaya + penyesuaian delay, simpan posisi otomatis, dan hitung
 * mundur putar episode berikutnya.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    vm: MainViewModel,
    titleId: String,
    episodeNumber: Int,
    onBack: () -> Unit,
    onNextEpisode: (String, Int) -> Unit,
) {
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findActivity() }
    val streamUrl by vm.streamUrl.collectAsState()
    val streamError by vm.streamError.collectAsState()
    val downloads by vm.downloads.collectAsState()
    val playerSettings by vm.playerSettings.collectAsState()
    val streamId = if (episodeNumber > 1) "$titleId-ep$episodeNumber" else titleId
    val dlProgress = downloads.firstOrNull { it.id == streamId || it.id == titleId }

    var title by remember { mutableStateOf<Title?>(null) }
    val episode = remember(title, episodeNumber) {
        title?.playlist()?.firstOrNull { it.number == episodeNumber } ?: Episode(episodeNumber)
    }
    val nextEpisode = remember(title, episodeNumber) {
        title?.playlist()?.firstOrNull { it.number == episodeNumber + 1 }
    }

    LaunchedEffect(titleId, episodeNumber) {
        val loaded = vm.title(titleId)
        title = loaded
        // Episode WAJIB diturunkan dari judul yang baru dimuat ini: memakai
        // nilai `episode` hasil komposisi sebelumnya (judul masih null)
        // menghasilkan Episode polos tanpa magnet per episode.
        loaded?.let { t ->
            val ep = t.playlist().firstOrNull { it.number == episodeNumber } ?: Episode(episodeNumber)
            vm.stream(t, ep)
        }
    }

    val player = remember { ExoPlayer.Builder(ctx).build() }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackEnded by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var tracks by remember { mutableStateOf<Tracks?>(null) }

    // Terapkan kecepatan bawaan dari pengaturan sekali di awal.
    LaunchedEffect(playerSettings.defaultSpeed) {
        player.setPlaybackSpeed(playerSettings.defaultSpeed)
    }

    // Gaya subtitle dari pengaturan pengguna.
    LaunchedEffect(playerSettings, playerView) {
        playerView?.subtitleView?.apply {
            setStyle(
                CaptionStyleCompat(
                    playerSettings.subtitleColorArgb, playerSettings.subtitleBackgroundArgb,
                    0, CaptionStyleCompat.EDGE_TYPE_NONE,
                    playerSettings.subtitleColorArgb, null
                )
            )
            setFractionalTextSize(0.0533f * playerSettings.subtitleTextSize)
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                playbackEnded = state == Player.STATE_ENDED
                durationMs = player.duration.coerceAtLeast(0)
            }
            override fun onTracksChanged(newTracks: Tracks) { tracks = newTracks }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }

    // Pulihkan posisi terakhir sekali per judul+episode.
    LaunchedEffect(streamUrl) {
        val url = streamUrl ?: return@LaunchedEffect
        val saved = vm.savedPosition(titleId)
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        if (saved > 0) player.seekTo(saved)
        player.play()
    }

    // Poll posisi + simpan riwayat tiap ~5 detik.
    LaunchedEffect(player) {
        var tick = 0
        while (isActive) {
            delay(1000)
            positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = player.duration.coerceAtLeast(0)
            if (++tick % 5 == 0 && player.isPlaying) {
                title?.let { vm.saveProgress(it, episode, positionMs, durationMs) }
            }
        }
    }

    // Mode sinema: landscape + bar sistem tersembunyi.
    DisposableEffect(Unit) {
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        if (window != null) WindowCompat.setDecorFitsSystemWindows(window, false)
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Status PiP untuk menyembunyikan kontrol saat masuk PiP.
    var inPip by remember { mutableStateOf(false) }
    LaunchedEffect(activity) {
        while (isActive) {
            inPip = activity?.isInPictureInPictureMode == true
            delay(500)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            streamUrl != null -> {
                AndroidView(
                    factory = {
                        PlayerView(it).apply {
                            this.player = player
                            useController = false
                            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                            playerView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                if (!inPip) {
                    PlayerControls(
                        player = player,
                        title = title,
                        episode = episode,
                        isPlaying = isPlaying,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        tracks = tracks,
                        hasNext = nextEpisode != null && title?.magnetFor(nextEpisode) != null,
                        ended = playbackEnded,
                        onBack = onBack,
                        onNext = { nextEpisode?.let { onNextEpisode(titleId, it.number) } },
                        onTogglePlay = { if (player.isPlaying) player.pause() else player.play() },
                    )
                }
            }
            streamError != null -> Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Video tidak bisa diputar", style = MaterialTheme.typography.titleMedium)
                Text(streamError ?: "", style = MaterialTheme.typography.bodySmall, color = FlixMuted, textAlign = TextAlign.Center)
                Button(
                    onClick = { title?.let { vm.stream(it, episode) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) { Text("Coba lagi", style = MaterialTheme.typography.labelLarge) }
            }
            else -> Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(color = FlixRed, strokeWidth = 3.dp)
                Text("Menyiapkan video…", style = MaterialTheme.typography.bodyMedium)
                if (dlProgress != null && dlProgress.downloadRate > 0) {
                    Text("${dlProgress.downloadRate / 1024} KB/s", style = MaterialTheme.typography.bodySmall, color = FlixMuted)
                }
            }
        }

        if (streamUrl == null) {
            GlassIconButton(
                Icons.AutoMirrored.Filled.ArrowBack, "Kembali", onBack,
                Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(8.dp)
            )
        }

        dlProgress?.let {
            LinearProgressIndicator(
                progress = { it.progress },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp),
                color = FlixRed, trackColor = Color.Transparent,
                gapSize = 0.dp, drawStopIndicator = {}
            )
        }
    }
}

/** Lapisan kontrol kustom di atas PlayerView (useController = false). */
@OptIn(UnstableApi::class)
@Composable
private fun PlayerControls(
    player: ExoPlayer,
    title: Title?,
    episode: Episode,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    tracks: Tracks?,
    hasNext: Boolean,
    ended: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onTogglePlay: () -> Unit,
) {
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findActivity() }
    var visible by remember { mutableStateOf(true) }
    var seekIndicator by remember { mutableStateOf<String?>(null) }
    var levelIndicator by remember { mutableStateOf<String?>(null) }
    var speed by remember { mutableFloatStateOf(player.playbackParameters.speed) }
    var showSpeed by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }
    var showSubs by remember { mutableStateOf(false) }

    // Sembunyikan kontrol otomatis saat memutar.
    LaunchedEffect(visible, isPlaying) {
        if (visible && isPlaying) { delay(3000); visible = false }
    }
    LaunchedEffect(seekIndicator) { if (seekIndicator != null) { delay(700); seekIndicator = null } }
    LaunchedEffect(levelIndicator) { if (levelIndicator != null) { delay(700); levelIndicator = null } }

    // ---- Subtitle sideload + delay ----
    var externalSubName by remember { mutableStateOf<String?>(null) }
    var subDelayMs by remember { mutableLongStateOf(0L) }
    var pendingSubUri by remember { mutableStateOf<Uri?>(null) }
    val subPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingSubUri = uri
    }
    LaunchedEffect(pendingSubUri, subDelayMs) {
        val uri = pendingSubUri ?: return@LaunchedEffect
        val name = uri.lastPathSegment ?: "subtitle.srt"
        externalSubName = name
        val currentUri = player.currentMediaItem?.localConfiguration?.uri ?: return@LaunchedEffect
        val keepPos = player.currentPosition
        val keepPlay = player.playWhenReady
        val subUri: Uri = runCatching {
            val raw = ctx.contentResolver.openInputStream(uri)?.readBytes() ?: return@runCatching uri
            if (name.endsWith(".srt", true) && subDelayMs != 0L) {
                val shifted = SubtitleShifter.shiftSrt(raw.decodeToString(), subDelayMs)
                val f = File(ctx.cacheDir, "subs/sub_shifted.srt").apply { parentFile?.mkdirs() }
                f.writeText(shifted)
                Uri.fromFile(f)
            } else uri
        }.getOrDefault(uri)
        val item = MediaItem.Builder()
            .setUri(currentUri)
            .setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(subUri)
                        .setMimeType(SubtitleShifter.mimeFor(name))
                        .setLanguage("id")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                )
            )
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.seekTo(keepPos)
        player.playWhenReady = keepPlay
    }

    Box(Modifier.fillMaxSize()) {
        // Lapisan gestur: ketuk = tampil/sembunyi, ketuk-ganda = ±10 dtk,
        // geser vertikal kiri = kecerahan, kanan = volume.
        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { visible = !visible },
                        onDoubleTap = { offset ->
                            if (offset.x < size.width / 2) {
                                player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                                seekIndicator = "-10 dtk"
                            } else {
                                player.seekTo(player.currentPosition + 10_000)
                                seekIndicator = "+10 dtk"
                            }
                            visible = true
                        }
                    )
                }
                .pointerInput(Unit) {
                    var leftSide = true
                    detectVerticalDragGestures(
                        onDragStart = { leftSide = it.x < size.width / 2 },
                        onVerticalDrag = { _, dragAmount ->
                            val window = activity?.window
                            if (leftSide) {
                                val attrs = window?.attributes
                                if (attrs != null) {
                                    val cur = if (attrs.screenBrightness < 0) 0.5f else attrs.screenBrightness
                                    attrs.screenBrightness = (cur - dragAmount / 1500f).coerceIn(0.01f, 1f)
                                    window.attributes = attrs
                                    levelIndicator = "Kecerahan ${(attrs.screenBrightness * 100).toInt()}%"
                                }
                            } else {
                                val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val next = (cur - dragAmount / 60f).toInt().coerceIn(0, max)
                                am.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                                levelIndicator = "Volume ${(next * 100) / max}%"
                            }
                        }
                    )
                }
        )

        // Indikator tengah (seek / kecerahan / volume).
        listOfNotNull(seekIndicator, levelIndicator).firstOrNull()?.let { text ->
            Surface(
                color = Color.Black.copy(alpha = 0.65f), shape = RoundedCornerShape(24.dp),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            }
        }

        if (visible) {
            // Bar atas: kembali + judul + PiP.
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .safeDrawingPadding().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", onBack)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title?.name ?: "", color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    if ((title?.playlist()?.size ?: 0) > 1) {
                        Text(episode.name, color = FlixMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                IconButton(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        activity?.enterPictureInPictureMode(
                            PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                        )
                    }
                }) {
                    Icon(Icons.Default.PictureInPictureAlt, "Picture-in-Picture", tint = Color.White)
                }
            }

            // Kontrol tengah: putar/jeda besar.
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier.align(Alignment.Center).size(76.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(38.dp))
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Jeda" else "Putar",
                    tint = Color.White, modifier = Modifier.size(44.dp)
                )
            }

            // Bar bawah: posisi, seek bar, waktu, menu trek/kecepatan.
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .safeDrawingPadding().padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                var scrubbing by remember { mutableStateOf<Float?>(null) }
                val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                Slider(
                    value = scrubbing ?: fraction,
                    onValueChange = { scrubbing = it },
                    onValueChangeFinished = {
                        scrubbing?.let { player.seekTo((it * durationMs).toLong()) }
                        scrubbing = null
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = FlixRed, activeTrackColor = FlixRed,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${formatTime(positionMs)} / ${formatTime(durationMs)}",
                        color = Color.White, style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showSpeed = true }) {
                        Text("${speed}x", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                    IconButton(onClick = { showAudio = true }) {
                        Icon(Icons.Default.Audiotrack, "Audio", tint = Color.White)
                    }
                    IconButton(onClick = { showSubs = true }) {
                        Icon(Icons.Default.Subtitles, "Subtitle", tint = Color.White)
                    }
                    if (hasNext) {
                        IconButton(onClick = onNext) {
                            Icon(Icons.Default.SkipNext, "Episode berikutnya", tint = Color.White)
                        }
                    }
                }
            }
        }

        // Hitung mundur episode berikutnya.
        if (ended && hasNext) {
            NextEpisodeCountdown(onNext = onNext, onCancel = { /* tetap di layar akhir */ })
        }
    }

    if (showSpeed) {
        AlertDialog(
            onDismissRequest = { showSpeed = false },
            title = { Text("Kecepatan putar") },
            text = {
                Column {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = speed == option, onClick = {
                                speed = option
                                player.setPlaybackSpeed(option)
                                showSpeed = false
                            })
                            Text("${option}x")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSpeed = false }) { Text("Tutup") } }
        )
    }

    if (showAudio) {
        TrackSelectionDialog(
            title = "Trek audio", tracks = tracks, type = C.TRACK_TYPE_AUDIO,
            onDismiss = { showAudio = false },
            onSelect = { group, index ->
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setOverrideForType(TrackSelectionOverride(group, index)).build()
                showAudio = false
            }
        )
    }

    if (showSubs) {
        AlertDialog(
            onDismissRequest = { showSubs = false },
            title = { Text("Subtitle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                        showSubs = false
                    }) { Text("Nonaktif") }
                    tracks?.groups?.filter { it.type == C.TRACK_TYPE_TEXT }?.forEach { group ->
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val label = format.label ?: format.language ?: "Trek ${i + 1}"
                            TextButton(onClick = {
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i)).build()
                                showSubs = false
                            }) { Text(if (group.isTrackSelected(i)) "✓ $label" else label) }
                        }
                    }
                    HorizontalDivider()
                    TextButton(onClick = { subPicker.launch("*/*"); showSubs = false }) {
                        Text("Muat berkas subtitle (SRT/ASS/VTT)…")
                    }
                    externalSubName?.let { name ->
                        Text("Aktif: $name", style = MaterialTheme.typography.bodySmall, color = FlixMuted)
                        Text("Delay subtitle: ${subDelayMs / 1000.0} dtk", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = subDelayMs.toFloat(),
                            onValueChange = { subDelayMs = (it / 500).toLong() * 500 },
                            valueRange = -5_000f..5_000f, steps = 19
                        )
                        Text(
                            "Delay hanya berlaku untuk subtitle SRT sideload. Gaya subtitle (ukuran/warna) diatur di Pengaturan.",
                            style = MaterialTheme.typography.bodySmall, color = FlixMuted
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSubs = false }) { Text("Tutup") } }
        )
    }
}

@Composable
private fun TrackSelectionDialog(
    title: String,
    tracks: Tracks?,
    type: Int,
    onDismiss: () -> Unit,
    onSelect: (group: androidx.media3.common.TrackGroup, index: Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                val groups = tracks?.groups?.filter { it.type == type }.orEmpty()
                if (groups.isEmpty()) {
                    Text("Tidak ada trek tersedia.", color = FlixMuted)
                }
                groups.forEach { group ->
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val label = format.label ?: format.language
                            ?: format.channelCount.let { if (it > 0) "Audio $it kanal" else "Trek ${i + 1}" }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = group.isTrackSelected(i),
                                onClick = { onSelect(group.mediaTrackGroup, i) }
                            )
                            Text(label)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

/** Kartu hitung mundur 10 detik menuju episode berikutnya (bisa dibatalkan dengan ketuk di luar/silang implisit lewat tombol Tetap). */
@Composable
private fun BoxScope.NextEpisodeCountdown(onNext: () -> Unit, onCancel: () -> Unit) {
    var remaining by remember { mutableIntStateOf(10) }
    var cancelled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (remaining > 0 && !cancelled) { delay(1000); remaining-- }
        if (!cancelled) onNext()
    }
    if (cancelled) return
    Surface(
        color = Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(12.dp),
        modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Episode berikutnya dalam $remaining dtk", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) { Text("Putar sekarang") }
                TextButton(onClick = { cancelled = true; onCancel() }) { Text("Tetap di sini", color = Color.White) }
            }
        }
    }
}
