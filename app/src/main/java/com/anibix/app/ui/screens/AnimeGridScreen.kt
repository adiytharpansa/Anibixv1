package com.anibix.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.anibix.app.ui.MainViewModel
import com.anibix.app.ui.components.AnimeGridCard
import com.anibix.app.ui.components.GlassIconButton
import com.anibix.app.ui.theme.FlixBlack
import com.anibix.app.ui.theme.FlixMuted
import com.anibix.app.ui.theme.FlixRed
import com.anibix.app.ui.theme.FlixSurface

@Composable
fun AnimeGridScreen(vm: MainViewModel, onTitle: (String) -> Unit, onBack: () -> Unit) {
    val titles by vm.titles.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(titles, query) {
        val q = query.trim()
        if (q.isEmpty()) titles else titles.filter { it.name.contains(q, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().background(FlixBlack).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", onBack)
            Text("Anime", style = MaterialTheme.typography.titleLarge)
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Cari anime…", color = FlixMuted) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = FlixMuted) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                    Icon(Icons.Filled.Close, contentDescription = "Hapus", tint = FlixMuted)
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(6.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = FlixSurface, unfocusedContainerColor = FlixSurface,
                focusedBorderColor = FlixRed, unfocusedBorderColor = Color.Transparent,
                cursorColor = FlixRed, focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                titles.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = FlixRed, strokeWidth = 3.dp)
                shown.isEmpty() ->
                    Text("Tidak ada hasil untuk \"$query\"", color = FlixMuted, modifier = Modifier.align(Alignment.Center))
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 108.dp), // 3 kolom di HP, lebih banyak di tablet
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(shown, key = { it.id }) { t -> AnimeGridCard(t, onClick = { onTitle(t.id) }) }
                }
            }
        }
    }
}
