package com.anibix.app.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.style.TextOverflow
import com.anibix.app.ui.theme.FlixMuted
import com.anibix.app.ui.theme.FlixSurfaceHigh
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import coil.compose.AsyncImage
import com.anibix.app.data.Title
import com.anibix.app.ui.theme.FlixSurface

/** "Animasi  •  2010" — skips empty parts. */
fun Title.meta(): String =
    listOfNotNull(genre.takeIf { it.isNotBlank() }, year.takeIf { it > 0 }?.toString())
        .joinToString("  •  ")

/** Poster tile that shrinks slightly while pressed. */
@Composable
fun PosterCard(t: Title, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "posterPress")
    AsyncImage(
        model = t.posterUrl,
        contentDescription = t.name,
        contentScale = ContentScale.Crop,
        placeholder = ColorPainter(FlixSurface),
        error = ColorPainter(FlixSurface),
        modifier = modifier
            .size(width = 128.dp, height = 192.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(6.dp))
            .background(FlixSurface)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
    )
}

/** Primary = white pill-ish button (Play). Secondary = translucent grey. */
@Composable
fun FlixButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) Color.White else Color(0xFF6D6D6E).copy(alpha = 0.7f),
            contentColor = if (primary) Color.Black else Color.White,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Round, semi-transparent button for floating over images (back, close). */
@Composable
fun GlassIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/** Grid tile: poster (2:3) + judul + meta. Lebar mengikuti sel grid. */
@Composable
fun AnimeGridCard(t: Title, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "gridPress")
    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null, onClick = onClick)
    ) {
        AsyncImage(
            model = t.posterUrl,
            contentDescription = t.name,
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(FlixSurface),
            error = ColorPainter(FlixSurface),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(6.dp))
                .background(FlixSurface)
        )
        Spacer(Modifier.height(6.dp))
        Text(t.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val meta = t.meta()
        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = FlixMuted, maxLines = 1)
    }
}

/** Kotak placeholder berkilau untuk keadaan memuat (skeleton). */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(6.dp)) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.LinearEasing),
            androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "shimmerShift"
    )
    val brush = Brush.linearGradient(
        colors = listOf(FlixSurface, FlixSurfaceHigh, FlixSurface),
        start = Offset(shift * 600f - 300f, 0f),
        end = Offset(shift * 600f, 0f)
    )
    Box(modifier.clip(shape).background(brush))
}

/** Keadaan error layar penuh dengan tombol coba lagi. */
@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Ada masalah", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = FlixMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
        ) { Text("Coba lagi", style = MaterialTheme.typography.labelLarge) }
    }
}
