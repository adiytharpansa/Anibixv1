package com.anibix.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anibix.app.torrent.TorrentState
import com.anibix.app.ui.MainViewModel
import com.anibix.app.ui.theme.FlixBlack
import com.anibix.app.ui.theme.FlixMuted
import com.anibix.app.ui.theme.FlixRed
import com.anibix.app.ui.theme.FlixSurface
import com.anibix.app.ui.theme.FlixSurfaceHigh

@Composable
fun DownloadsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val items by vm.downloads.collectAsState()

    Column(Modifier.fillMaxSize().background(FlixBlack).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
            }
            Text("Download", style = MaterialTheme.typography.headlineSmall)
        }

        if (items.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Outlined.Download, null, Modifier.size(56.dp), tint = FlixMuted)
                Spacer(Modifier.height(16.dp))
                Text("Belum ada unduhan", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Buka halaman judul lalu pilih Download untuk menontonnya tanpa internet.",
                    style = MaterialTheme.typography.bodyMedium, color = FlixMuted, textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.id }) { d ->
                    DownloadRow(d, onPause = { vm.pause(d.id) }, onResume = { vm.resume(d.id) }, onRemove = { vm.remove(d.id) })
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(d: TorrentState, onPause: () -> Unit, onResume: () -> Unit, onRemove: () -> Unit) {
    Surface(color = FlixSurface, shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    d.title, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                if (!d.finished) {
                    IconButton(onClick = onPause) { Icon(Icons.Default.Pause, "Jeda") }
                    IconButton(onClick = onResume) { Icon(Icons.Default.PlayArrow, "Lanjutkan") }
                }
                IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, "Hapus") }
            }
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(
                    progress = { d.progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = FlixRed, trackColor = FlixSurfaceHigh,
                    gapSize = 0.dp, drawStopIndicator = {}
                )
                Text(
                    if (d.finished) "Selesai" else "${(d.progress * 100).toInt()}%  •  ${formatRate(d.downloadRate)}",
                    style = MaterialTheme.typography.bodySmall, color = FlixMuted
                )
            }
        }
    }
}

private fun formatRate(bytesPerSec: Int): String =
    if (bytesPerSec >= 1_048_576) "%.1f MB/s".format(bytesPerSec / 1_048_576f)
    else "${bytesPerSec / 1024} KB/s"
