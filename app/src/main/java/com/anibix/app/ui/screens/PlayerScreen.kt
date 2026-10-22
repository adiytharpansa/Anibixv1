package com.anibix.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.anibix.app.data.Title
import com.anibix.app.ui.MainViewModel
import com.anibix.app.ui.components.GlassIconButton
import com.anibix.app.ui.theme.FlixMuted
import com.anibix.app.ui.theme.FlixRed

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(vm: MainViewModel, id: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val url by vm.streamUrl.collectAsState()
    val error by vm.error.collectAsState()
    val downloads by vm.downloads.collectAsState()
    val progress = downloads.firstOrNull { it.id == id }
    var title by remember { mutableStateOf<Title?>(null) }

    LaunchedEffect(id) {
        title = vm.title(id)
        title?.let { vm.stream(it) }
    }

    val player = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(url) {
        url?.let { player.setMediaItem(MediaItem.fromUri(it)); player.prepare(); player.play() }
    }

    // Cinema mode: landscape + hidden system bars while the player is open.
    DisposableEffect(Unit) {
        val activity = ctx.findActivity()
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            url != null -> AndroidView(
                factory = { PlayerView(it).apply {
                    this.player = player
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                } },
                modifier = Modifier.fillMaxSize()
            )
            error != null -> Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Video tidak bisa diputar", style = MaterialTheme.typography.titleMedium)
                Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = FlixMuted, textAlign = TextAlign.Center)
                Button(
                    onClick = { title?.let { vm.stream(it) } },
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
                if (progress != null && progress.downloadRate > 0) {
                    Text("${progress.downloadRate / 1024} KB/s", style = MaterialTheme.typography.bodySmall, color = FlixMuted)
                }
            }
        }

        GlassIconButton(
            Icons.AutoMirrored.Filled.ArrowBack, "Kembali", onBack,
            Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(8.dp)
        )

        // Thin red line = how much of the file has been downloaded so far.
        progress?.let {
            LinearProgressIndicator(
                progress = { it.progress },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp),
                color = FlixRed, trackColor = Color.Transparent,
                gapSize = 0.dp, drawStopIndicator = {}
            )
        }
    }
}
