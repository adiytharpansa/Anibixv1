package com.anibix.app.ui.screens

import android.view.WindowManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.anibix.app.data.Episode
import com.anibix.app.data.Title
import com.anibix.app.ui.MainViewModel
import com.anibix.app.ui.components.GlassIconButton
import com.anibix.app.ui.player.PlaybackSnapshot
import com.anibix.app.ui.player.PlayerOverlay
import com.anibix.app.ui.player.enterPictureInPicture
import com.anibix.app.ui.player.findActivity
import com.anibix.app.ui.player.formatTime
import com.anibix.app.ui.player.resumePositionFor
import com.anibix.app.ui.theme.FlixMuted
import com.anibix.app.ui.theme.FlixRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pemutar Media3 gaya Netflix. UI kontrolnya ada di paket `ui.player`
 * (PlayerOverlay, PlayerSeekBar, PlayerPanels); layar ini mengurus siklus
 * hidup ExoPlayer, pemulihan posisi, simpan riwayat, retry otomatis,
 * mode sinema, dan Picture-in-Picture.
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
    val componentActivity = activity as? ComponentActivity
    val scope = rememberCoroutineScope()
    val streamUrl by vm.streamUrl.collectAsState()
    val streamError by vm.streamError.collectAsState()
    val downloads by vm.downloads.collectAsState()
    val playerSettings by vm.playerSettings.collectAsState()

    var title by remember { mutableStateOf<Title?>(null) }
    val playlist = remember(title) { title?.playlist().orEmpty() }
    val episode = remember(playlist, episodeNumber) {
        playlist.firstOrNull { it.number == episodeNumber } ?: Episode(episodeNumber)
    }
    val nextEpisode = remember(playlist, episodeNumber) {
        playlist.firstOrNull { it.number == episodeNumber + 1 }
    }
    val streamId = if (playlist.size > 1) "$titleId-ep$episodeNumber" else titleId
    val dlProgress = downloads.firstOrNull { it.id == streamId }

    // Selalu mulai dari keadaan bersih: URL stream episode sebelumnya tidak
    // boleh terputar lagi sesaat saat berpindah episode.
    LaunchedEffect(titleId, episodeNumber) {
        vm.resetStream()
        val loaded = vm.title(titleId)
        title = loaded
        if (loaded == null) {
            vm.failStream("Judul tidak ditemukan.")
            return@LaunchedEffect
        }
        // Episode WAJIB diturunkan dari judul yang baru dimuat ini (magnet per episode).
        val ep = loaded.playlist().firstOrNull { it.number == episodeNumber } ?: Episode(episodeNumber)
        vm.stream(loaded, ep)
    }

    // ---- ExoPlayer: buffer besar untuk torrent, audio focus, jeda saat headset dicabut ----
    val player = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(20_000, 60_000, 2_000, 5_000)
            .build()
        ExoPlayer.Builder(ctx)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { p ->
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                    .setPreferredTextLanguages("id", "in", "en")
                    .setSelectUndeterminedTextLanguage(true)
                    .build()
            }
    }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var playbackEnded by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var bufferedMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableStateOf(1.0f) }
    var tracks by remember { mutableStateOf<Tracks?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }
    var resumeHud by remember { mutableStateOf<String?>(null) }

    fun saveNow() {
        val t = title ?: return
        val pos = player.currentPosition.coerceAtLeast(0L)
        if (pos < 2_000L) return
        vm.saveProgress(t, Episode(episodeNumber), pos, player.duration.coerceAtLeast(0L))
    }

    // Kecepatan bawaan dari pengaturan.
    LaunchedEffect(playerSettings.defaultSpeed) { player.setPlaybackSpeed(playerSettings.defaultSpeed) }

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
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) retryCount = 0
            }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                playbackEnded = state == Player.STATE_ENDED
                durationMs = player.duration.coerceAtLeast(0L)
            }
            override fun onTracksChanged(newTracks: Tracks) { tracks = newTracks }
            override fun onPlaybackParametersChanged(p: PlaybackParameters) { speed = p.speed }
            override fun onPlayerError(error: PlaybackException) {
                // Torrent bisa tersendat sesaat: coba lagi otomatis (posisi dipertahankan).
                if (retryCount < 3) {
                    retryCount++
                    val wait = 1_500L * retryCount
                    scope.launch { delay(wait); player.prepare() }
                } else {
                    playbackError = "Pemutaran terhenti (${error.errorCodeName})."
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            saveNow()
            player.release()
        }
    }

    // Muat stream + pulihkan posisi (hanya bila riwayat milik episode INI).
    LaunchedEffect(streamUrl) {
        val url = streamUrl ?: return@LaunchedEffect
        playbackError = null
        retryCount = 0
        val saved = vm.savedProgress(titleId)
        val start = resumePositionFor(
            savedEpisode = saved?.episodeNumber ?: 0,
            currentEpisode = episodeNumber,
            savedPositionMs = saved?.positionMs ?: 0L,
            savedDurationMs = saved?.durationMs ?: 0L,
        )
        if (start > 0) resumeHud = "Lanjut dari ${formatTime(start)}"
        player.setMediaItem(MediaItem.fromUri(url), start)
        player.prepare()
        player.play()
    }

    // Polling posisi + simpan riwayat tiap ~5 dtk.
    LaunchedEffect(player) {
        var tick = 0
        while (isActive) {
            delay(500)
            positionMs = player.currentPosition.coerceAtLeast(0L)
            bufferedMs = player.bufferedPosition.coerceAtLeast(0L)
            durationMs = player.duration.coerceAtLeast(0L)
            if (++tick % 10 == 0 && player.isPlaying) saveNow()
        }
    }

    // Simpan + jeda saat app ke latar (kecuali sedang PiP).
    val lifecycle = componentActivity?.lifecycle
    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                saveNow()
                if (activity?.isInPictureInPictureMode != true) player.pause()
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    // Mode sinema: landscape, bar sistem tersembunyi, isi sampai area poni (notch).
    DisposableEffect(Unit) {
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val attrs = window.attributes
                attrs.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                window.attributes = attrs
            }
        }
        onDispose {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (window != null) {
                val attrs = window.attributes
                // Kembalikan kecerahan ke pengaturan sistem (gestur kecerahan tidak boleh bocor ke layar lain).
                attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    attrs.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
                window.attributes = attrs
            }
        }
    }

    // PiP: masuk otomatis saat tombol Home ditekan (sedang memutar) + pantau statusnya.
    var inPip by remember { mutableStateOf(false) }
    DisposableEffect(componentActivity, player) {
        val pipListener = Consumer<PictureInPictureModeChangedInfo> { info ->
            inPip = info.isInPictureInPictureMode
        }
        val leaveHint = Runnable { if (player.isPlaying) enterPictureInPicture(activity, player) }
        componentActivity?.addOnPictureInPictureModeChangedListener(pipListener)
        componentActivity?.addOnUserLeaveHintListener(leaveHint)
        onDispose {
            componentActivity?.removeOnPictureInPictureModeChangedListener(pipListener)
            componentActivity?.removeOnUserLeaveHintListener(leaveHint)
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
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER) // spinner digambar overlay
                            keepScreenOn = true
                            playerView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                PlayerOverlay(
                    player = player,
                    playerView = playerView,
                    state = PlaybackSnapshot(
                        isPlaying = isPlaying, isBuffering = isBuffering, ended = playbackEnded,
                        positionMs = positionMs, bufferedMs = bufferedMs, durationMs = durationMs,
                        speed = speed,
                    ),
                    title = title,
                    episode = episode,
                    tracks = tracks,
                    nextEpisode = nextEpisode,
                    hasNext = nextEpisode != null && title?.magnetFor(nextEpisode) != null,
                    isEpisodeAvailable = { ep -> title?.magnetFor(ep) != null },
                    downloadRateKb = (dlProgress?.downloadRate ?: 0) / 1024L,
                    inPip = inPip,
                    initialHud = resumeHud,
                    onBack = onBack,
                    onNext = { nextEpisode?.let { onNextEpisode(titleId, it.number) } },
                    onSelectEpisode = { n -> onNextEpisode(titleId, n) },
                    onEnterPip = { enterPictureInPicture(activity, player) },
                )
                playbackError?.let { msg ->
                    Column(
                        Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.85f)).padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Video tidak bisa diputar", style = MaterialTheme.typography.titleMedium)
                        Text(msg, style = MaterialTheme.typography.bodySmall, color = FlixMuted, textAlign = TextAlign.Center)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { playbackError = null; retryCount = 0; player.prepare() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                            ) { Text("Coba lagi", style = MaterialTheme.typography.labelLarge) }
                            TextButton(onClick = onBack) { Text("Kembali", color = Color.White) }
                        }
                    }
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
            else -> PreparingView(title, (dlProgress?.downloadRate ?: 0) / 1024L)
        }

        if (streamUrl == null) {
            GlassIconButton(
                Icons.AutoMirrored.Filled.ArrowBack, "Kembali", onBack,
                Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(8.dp)
            )
        }
    }
}

/** Layar persiapan: backdrop judul meredup + spinner merah + kecepatan unduh. */
@Composable
private fun PreparingView(title: Title?, rateKb: Long) {
    Box(Modifier.fillMaxSize()) {
        if (title != null && title.backdropUrl.isNotBlank()) {
            AsyncImage(
                model = title.backdropUrl, contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.35f)
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.92f)))
            )
        )
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(color = FlixRed, strokeWidth = 3.dp)
            if (title != null) Text(title.name, style = MaterialTheme.typography.titleMedium)
            Text("Menyiapkan video…", style = MaterialTheme.typography.bodyMedium, color = FlixMuted)
            if (rateKb > 0) Text("$rateKb KB/s", style = MaterialTheme.typography.bodySmall, color = FlixMuted)
        }
    }
}
