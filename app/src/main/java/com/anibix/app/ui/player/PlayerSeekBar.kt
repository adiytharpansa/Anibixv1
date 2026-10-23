package com.anibix.app.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.anibix.app.ui.theme.FlixRed
import kotlin.math.roundToInt

/**
 * Seek bar gaya Netflix: garis tipis merah, lapisan buffer, thumb yang
 * membesar saat digeser, dan gelembung waktu di atas thumb.
 */
@Composable
internal fun PlayerSeekBar(
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    onScrubbingChanged: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var widthPx by remember { mutableIntStateOf(0) }
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnScrubbing by rememberUpdatedState(onScrubbingChanged)
    val duration by rememberUpdatedState(durationMs)

    val padPx = with(density) { 10.dp.toPx() }
    val scrubbing = scrubFraction != null
    val trackHeight by animateDpAsState(if (scrubbing) 5.dp else 3.dp, label = "seekTrack")
    val thumbRadius by animateDpAsState(if (scrubbing) 10.dp else 6.dp, label = "seekThumb")

    fun xToFraction(x: Float, width: Int): Float {
        val track = width - 2 * padPx
        return if (track <= 0f) 0f else ((x - padPx) / track).coerceIn(0f, 1f)
    }

    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val buffered = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shown = scrubFraction ?: progress

    Box(
        modifier
            .height(40.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (duration > 0) {
                        currentOnSeek((xToFraction(offset.x, size.width) * duration).toLong())
                    }
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        scrubFraction = xToFraction(offset.x, size.width)
                        currentOnScrubbing(true)
                    },
                    onDragEnd = {
                        scrubFraction?.let { f ->
                            if (duration > 0) currentOnSeek((f * duration).toLong())
                        }
                        scrubFraction = null
                        currentOnScrubbing(false)
                    },
                    onDragCancel = {
                        scrubFraction = null
                        currentOnScrubbing(false)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        scrubFraction = xToFraction(change.position.x, size.width)
                    }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val th = trackHeight.toPx()
            val trackW = size.width - 2 * padPx
            val corner = CornerRadius(th / 2f, th / 2f)
            drawRoundRect(
                Color.White.copy(alpha = 0.3f), Offset(padPx, cy - th / 2f),
                Size(trackW, th), corner
            )
            drawRoundRect(
                Color.White.copy(alpha = 0.55f), Offset(padPx, cy - th / 2f),
                Size(trackW * buffered, th), corner
            )
            drawRoundRect(
                FlixRed, Offset(padPx, cy - th / 2f),
                Size(trackW * shown, th), corner
            )
            drawCircle(FlixRed, thumbRadius.toPx(), Offset(padPx + trackW * shown, cy))
        }

        scrubFraction?.let { f ->
            val bubbleW = 64.dp
            Text(
                formatTime((f * durationMs).toLong()),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset {
                        val bw = bubbleW.roundToPx()
                        val x = (padPx + (widthPx - 2 * padPx) * f - bw / 2f)
                            .coerceIn(0f, (widthPx - bw).toFloat().coerceAtLeast(0f))
                        IntOffset(x.roundToInt(), -40.dp.roundToPx())
                    }
                    .width(bubbleW)
                    .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                    .padding(vertical = 4.dp)
            )
        }
    }
}
