package com.anibix.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anibix.app.data.Title
import com.anibix.app.ui.MainViewModel
import com.anibix.app.ui.components.FlixButton
import com.anibix.app.ui.components.GlassIconButton
import com.anibix.app.ui.components.meta
import com.anibix.app.ui.theme.FlixBlack
import com.anibix.app.ui.theme.FlixMuted
import com.anibix.app.ui.theme.FlixRed
import com.anibix.app.ui.theme.FlixSurface

@Composable
fun DetailScreen(vm: MainViewModel, id: String, onPlay: (String, Int) -> Unit, onBack: () -> Unit) {
    var title by remember { mutableStateOf<Title?>(null) }
    val downloads by vm.downloads.collectAsState()
    LaunchedEffect(id) { title = vm.title(id) }

    val t = title
    if (t == null) {
        Box(Modifier.fillMaxSize().background(FlixBlack), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = FlixRed, strokeWidth = 3.dp)
        }
        return
    }
    val bookmarked by vm.isBookmarked(t.id).collectAsState(initial = false)
    // Keadaan unduhan judul ini: film = satu entri; serial = gabungan entri
    // per episode ("<id>-ep<n>") — progres dirata-rata, selesai bila semua selesai.
    val dlStates = downloads.filter { it.id == t.id || it.id.startsWith("${t.id}-ep") }
    val dl = when {
        dlStates.isEmpty() -> null
        dlStates.size == 1 -> dlStates.first()
        else -> dlStates.first().copy(
            progress = dlStates.map { it.progress }.average().toFloat(),
            finished = dlStates.all { it.finished },
        )
    }
    val playlist = t.playlist()
    val isSeries = playlist.size > 1

    Box(Modifier.fillMaxSize().background(FlixBlack)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(440.dp)) {
                AsyncImage(
                    model = t.backdropUrl, contentDescription = t.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop, alignment = Alignment.TopCenter
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.45f),
                            0.3f to Color.Transparent,
                            0.6f to Color.Transparent,
                            1f to FlixBlack
                        )
                    )
                )
                Column(
                    Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(t.name, style = MaterialTheme.typography.displaySmall)
                    val metaParts = listOfNotNull(
                        t.meta().takeIf { it.isNotEmpty() },
                        if (t.rating > 0) "★ ${"%.1f".format(t.rating)}" else null,
                        if (isSeries) "${playlist.size} episode" else null
                    )
                    Text(metaParts.joinToString("  •  "), style = MaterialTheme.typography.bodySmall, color = FlixMuted)
                }
            }

            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FlixButton("Putar", Icons.Default.PlayArrow, { onPlay(t.id, playlist.first().number) }, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        dl == null -> FlixButton(
                            "Download", Icons.Outlined.Download, { vm.download(t) },
                            Modifier.weight(1f), primary = false
                        )
                        dl.finished -> FlixButton(
                            "Sudah diunduh", Icons.Default.Check, {},
                            Modifier.weight(1f), primary = false
                        )
                        else -> FlixButton(
                            "Mengunduh ${(dl.progress * 100).toInt()}%", Icons.Outlined.Download, {},
                            Modifier.weight(1f), primary = false
                        )
                    }
                    FlixButton(
                        if (bookmarked) "Di Daftarku" else "Daftarku",
                        if (bookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                        { vm.toggleBookmark(t, bookmarked) },
                        Modifier.weight(1f), primary = false
                    )
                }
                if (t.synopsis.isNotBlank()) {
                    Text(
                        t.synopsis, style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                if (isSeries) {
                    Spacer(Modifier.height(10.dp))
                    Text("Episode", style = MaterialTheme.typography.titleMedium)
                    // Grid episode dalam kolom scroll induk: pakai grid tinggi tetap per baris.
                    val rows = (playlist.size + 3) / 4
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxWidth().height((rows * 64).dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        userScrollEnabled = false
                    ) {
                        items(playlist, key = { it.number }) { ep ->
                            Box(
                                Modifier.clip(RoundedCornerShape(6.dp)).background(FlixSurface)
                                    .clickable { onPlay(t.id, ep.number) }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${ep.number}", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }

        GlassIconButton(
            Icons.AutoMirrored.Filled.ArrowBack, "Kembali", onBack,
            Modifier.statusBarsPadding().padding(12.dp)
        )
    }
}
