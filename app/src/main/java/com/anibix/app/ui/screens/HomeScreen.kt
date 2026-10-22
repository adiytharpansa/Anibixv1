package com.anibix.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.anibix.app.ui.MainViewModel
import com.anibix.app.ui.components.FlixButton
import com.anibix.app.ui.components.PosterCard
import com.anibix.app.ui.components.meta
import com.anibix.app.ui.theme.FlixBlack
import com.anibix.app.ui.theme.FlixMuted
import com.anibix.app.ui.theme.FlixRed

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onTitle: (String) -> Unit,
    onPlay: (String) -> Unit,
    onGrid: () -> Unit,
    onDownloads: () -> Unit,
) {
    val categories by vm.categories.collectAsState()
    val downloads by vm.downloads.collectAsState()
    val listState = rememberLazyListState()

    // Top bar turns from transparent gradient to solid as the hero scrolls away.
    val barAlpha by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 500f).coerceIn(0f, 1f)
        }
    }
    val featured = categories.firstOrNull()?.titles?.firstOrNull()

    Box(Modifier.fillMaxSize().background(FlixBlack)) {
        if (categories.isEmpty()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = FlixRed, strokeWidth = 3.dp)
        } else {
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(22.dp)) {
                if (featured != null) {
                    item {
                        Hero(featured, onPlay = { onPlay(featured.id) }, onInfo = { onTitle(featured.id) })
                    }
                }
                items(categories, key = { it.name }) { cat ->
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

        TopBar(
            alpha = barAlpha,
            activeDownloads = downloads.count { !it.finished },
            onGrid = onGrid,
            onDownloads = onDownloads,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun TopBar(alpha: Float, activeDownloads: Int, onGrid: () -> Unit, onDownloads: () -> Unit, modifier: Modifier = Modifier) {
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
        // Scrim: darkens under the top bar and melts into the page at the bottom.
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
            Text(t.meta(), style = MaterialTheme.typography.bodySmall, color = FlixMuted)
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
