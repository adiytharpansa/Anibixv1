package com.anibix.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.anibix.app.data.Title
import com.anibix.app.data.local.WatchHistoryEntity
import com.anibix.app.ui.HomeUiState
import com.anibix.app.ui.MainViewModel
import com.anibix.app.ui.components.ErrorState
import com.anibix.app.ui.components.FlixButton
import com.anibix.app.ui.components.PosterCard
import com.anibix.app.ui.components.ShimmerBox
import com.anibix.app.ui.components.meta
import com.anibix.app.ui.theme.FlixBlack
import com.anibix.app.ui.theme.FlixMuted
import com.anibix.app.ui.theme.FlixRed

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onTitle: (String) -> Unit,
    onPlay: (String) -> Unit,
    onPlayEpisode: (String, Int) -> Unit,
    onGrid: () -> Unit,
    onDownloads: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by vm.homeState.collectAsState()
    val downloads by vm.downloads.collectAsState()
    val continueWatching by vm.continueWatching.collectAsState()
    val listState = rememberLazyListState()

    val barAlpha by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 500f).coerceIn(0f, 1f)
        }
    }

    Box(Modifier.fillMaxSize().background(FlixBlack)) {
        when (val s = state) {
            HomeUiState.Loading -> HomeShimmer()
            is HomeUiState.Error -> ErrorState(s.message, onRetry = { vm.loadHome() })
            is HomeUiState.Content -> {
                val featured = s.categories.firstOrNull()?.titles?.firstOrNull()
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    if (featured != null) {
                        item {
                            Hero(featured, onPlay = { onPlay(featured.id) }, onInfo = { onTitle(featured.id) })
                        }
                    }
                    if (continueWatching.isNotEmpty()) {
                        item {
                            ContinueWatchingRow(continueWatching, onResume = { onPlayEpisode(it.titleId, it.episodeNumber) })
                        }
                    }
                    items(s.categories, key = { it.name }) { cat ->
                        Column {
                            Text(
                                cat.name, style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 16.dp, bottom = 10.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(cat.titles, key = { it.id }) { t ->
                                    PosterCard(t, onClick = { onTitle(t.id) })
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.navigationBarsPadding().height(16.dp)) }
                }
            }
        }

        TopBar(
            alpha = barAlpha,
            activeDownloads = downloads.count { !it.finished },
            onGrid = onGrid,
            onDownloads = onDownloads,
            onSettings = onSettings,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun HomeShimmer() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        ShimmerBox(Modifier.fillMaxWidth().height(560.dp), shape = RoundedCornerShape(0.dp))
        repeat(2) {
            Column {
                ShimmerBox(Modifier.padding(start = 16.dp).width(140.dp).height(20.dp))
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(3) { ShimmerBox(Modifier.size(width = 128.dp, height = 192.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(items: List<WatchHistoryEntity>, onResume: (WatchHistoryEntity) -> Unit) {
    Column {
        Text(
            "Lanjutkan Menonton", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, bottom = 10.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = { it.titleId }) { h ->
                Column(
                    Modifier.width(200.dp).clickable { onResume(h) }
                ) {
                    Box {
                        AsyncImage(
                            model = h.posterUrl, contentDescription = h.titleName,
                            modifier = Modifier.fillMaxWidth().height(112.dp)
                                .clip(RoundedCornerShape(6.dp)).background(Color.DarkGray),
                            contentScale = ContentScale.Crop
                        )
                        Icon(
                            Icons.Default.PlayArrow, contentDescription = null, tint = Color.White,
                            modifier = Modifier.align(Alignment.Center).size(36.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { if (h.durationMs > 0) h.positionMs.toFloat() / h.durationMs else 0f },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = FlixRed, trackColor = Color.DarkGray,
                        gapSize = 0.dp, drawStopIndicator = {}
                    )
                    Text(
                        h.titleName, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (h.episodeNumber > 1) {
                        Text("Episode ${h.episodeNumber}", style = MaterialTheme.typography.bodySmall, color = FlixMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    alpha: Float, activeDownloads: Int,
    onGrid: () -> Unit, onDownloads: () -> Unit, onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
            .background(FlixBlack.copy(alpha = alpha))
            .statusBarsPadding()
            .height(56.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "ANIBIX", color = FlixRed, fontSize = 26.sp, fontWeight = FontWeight.Black,
            letterSpacing = 1.sp, modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onGrid) {
            Icon(Icons.Outlined.GridView, contentDescription = "Semua anime", tint = Color.White)
        }
        IconButton(onClick = onDownloads) {
            BadgedBox(badge = {
                if (activeDownloads > 0) Badge(containerColor = FlixRed, contentColor = Color.White) {
                    Text("$activeDownloads")
                }
            }) {
                Icon(Icons.Outlined.Download, contentDescription = "Download", tint = Color.White)
            }
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = "Pengaturan", tint = Color.White)
        }
    }
}

@Composable
private fun Hero(t: Title, onPlay: () -> Unit, onInfo: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(560.dp)) {
        AsyncImage(
            model = t.backdropUrl, contentDescription = t.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop, alignment = Alignment.TopCenter
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.5f),
                    0.25f to Color.Transparent,
                    0.55f to Color.Transparent,
                    1f to FlixBlack
                )
            )
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                t.name, style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            val metaParts = listOfNotNull(
                t.meta().takeIf { it.isNotEmpty() },
                if (t.rating > 0) "★ ${"%.1f".format(t.rating)}" else null
            )
            Text(metaParts.joinToString("  •  "), style = MaterialTheme.typography.bodySmall, color = FlixMuted)
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FlixButton("Putar", Icons.Default.PlayArrow, onPlay, Modifier.weight(1f))
                FlixButton("Info", Icons.Outlined.Info, onInfo, Modifier.weight(1f), primary = false)
            }
        }
    }
}
