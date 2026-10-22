package com.anibix.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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

@Composable
fun DetailScreen(vm: MainViewModel, id: String, onPlay: (String) -> Unit, onBack: () -> Unit) {
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
    val dl = downloads.firstOrNull { it.id == t.id }

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
                    Text(t.meta(), style = MaterialTheme.typography.bodySmall, color = FlixMuted)
                }
            }

            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FlixButton("Putar", Icons.Default.PlayArrow, { onPlay(t.id) }, Modifier.fillMaxWidth())
                when {
                    dl == null -> FlixButton(
                        "Download", Icons.Outlined.Download, { vm.download(t) },
                        Modifier.fillMaxWidth(), primary = false
                    )
                    dl.finished -> FlixButton(
                        "Sudah diunduh", Icons.Default.Check, {},
                        Modifier.fillMaxWidth(), primary = false
                    )
                    else -> FlixButton(
                        "Mengunduh ${(dl.progress * 100).toInt()}%", Icons.Outlined.Download, {},
                        Modifier.fillMaxWidth(), primary = false
                    )
                }
                Text(
                    t.synopsis, style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }

        GlassIconButton(
            Icons.AutoMirrored.Filled.ArrowBack, "Kembali", onBack,
            Modifier.statusBarsPadding().padding(12.dp)
        )
    }
}
