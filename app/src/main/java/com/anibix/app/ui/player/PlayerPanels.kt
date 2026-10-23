package com.anibix.app.ui.player

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.anibix.app.data.Episode
import com.anibix.app.ui.theme.FlixMuted
import com.anibix.app.ui.theme.FlixRed

/** Panel samping gaya Netflix: Episode, Audio & Subtitle, Kecepatan. */
@OptIn(UnstableApi::class)
@Composable
internal fun PlayerSidePanel(
    panel: PlayerPanel,
    player: ExoPlayer,
    tracks: Tracks?,
    speed: Float,
    playlist: List<Episode>,
    currentEpisode: Int,
    isAvailable: (Episode) -> Boolean,
    externalSubName: String?,
    subDelayMs: Long,
    onSubDelayChange: (Long) -> Unit,
    onPickSubtitle: () -> Unit,
    onSelectEpisode: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wide = panel == PlayerPanel.AudioSubtitles
    Column(
        modifier
            .fillMaxHeight()
            .fillMaxWidth(if (wide) 0.62f else 0.42f)
            .background(Color(0xF2121212))
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (panel) {
                    PlayerPanel.Episodes -> "Episode"
                    PlayerPanel.AudioSubtitles -> "Audio & Subtitle"
                    PlayerPanel.Speed -> "Kecepatan putar"
                    PlayerPanel.None -> ""
                },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Tutup", tint = Color.White)
            }
        }
        Spacer(Modifier.height(8.dp))

        when (panel) {
            PlayerPanel.Episodes -> {
                val index = playlist.indexOfFirst { it.number == currentEpisode }
                val listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = (index - 1).coerceAtLeast(0)
                )
                LazyColumn(state = listState) {
                    items(playlist) { ep ->
                        val current = ep.number == currentEpisode
                        val available = isAvailable(ep)
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable(enabled = available && !current) { onSelectEpisode(ep.number) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${ep.number}",
                                color = if (current) FlixRed else FlixMuted,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.width(40.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    episodeLabel(ep.number, ep.name).removePrefix("E${ep.number}: "),
                                    color = if (available) Color.White else FlixMuted,
                                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (current) {
                                    Text("Sedang diputar", color = FlixRed, style = MaterialTheme.typography.bodySmall)
                                } else if (!available) {
                                    Text("Belum tersedia", color = FlixMuted, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    }
                }
            }

            PlayerPanel.Speed -> {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PLAYBACK_SPEEDS.forEach { option ->
                        ChoiceRow(
                            label = speedLabel(option) + if (option == 1.0f) " (Normal)" else "",
                            selected = speed == option,
                            onClick = {
                                player.setPlaybackSpeed(option)
                                onClose()
                            }
                        )
                    }
                }
            }

            PlayerPanel.AudioSubtitles -> {
                val audioGroups = tracks?.groups?.filter { it.type == C.TRACK_TYPE_AUDIO }.orEmpty()
                val textGroups = tracks?.groups?.filter { it.type == C.TRACK_TYPE_TEXT }.orEmpty()
                val subsOff = textGroups.none { it.isSelected }

                Row(Modifier.fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Text("Audio", color = FlixMuted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        if (audioGroups.isEmpty()) {
                            Text("Tidak ada trek audio.", color = FlixMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        audioGroups.forEach { group ->
                            for (i in 0 until group.length) {
                                val format = group.getTrackFormat(i)
                                ChoiceRow(
                                    label = trackLabel(format, i) + channelSuffix(format),
                                    selected = group.isTrackSelected(i),
                                    onClick = {
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                            .build()
                                        onClose()
                                    }
                                )
                            }
                        }
                    }

                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Text("Subtitle", color = FlixMuted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        ChoiceRow(
                            label = "Mati",
                            selected = subsOff,
                            onClick = {
                                player.trackSelectionParameters = player.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                    .build()
                                onClose()
                            }
                        )
                        textGroups.forEach { group ->
                            for (i in 0 until group.length) {
                                ChoiceRow(
                                    label = trackLabel(group.getTrackFormat(i), i),
                                    selected = group.isTrackSelected(i),
                                    onClick = {
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                            .build()
                                        onClose()
                                    }
                                )
                            }
                        }
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.1f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        TextButton(onClick = onPickSubtitle) {
                            Text("Muat berkas subtitle (SRT/ASS/VTT)…", color = Color.White)
                        }
                        if (externalSubName != null) {
                            Text(
                                "Aktif: $externalSubName",
                                color = FlixMuted, style = MaterialTheme.typography.bodySmall
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onSubDelayChange(subDelayMs - 500) }) {
                                    Text("−0,5", color = Color.White)
                                }
                                Text(
                                    "Delay ${subDelayMs / 1000.0} dtk",
                                    color = Color.White, style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { onSubDelayChange(subDelayMs + 500) }) {
                                    Text("+0,5", color = Color.White)
                                }
                            }
                            Text(
                                "Delay hanya untuk subtitle SRT yang dimuat manual. Gaya subtitle diatur di Pengaturan.",
                                color = FlixMuted, style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            PlayerPanel.None -> Unit
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(28.dp)) {
            if (selected) Icon(Icons.Default.Check, null, tint = Color.White)
        }
        Text(
            label,
            color = if (selected) Color.White else FlixMuted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
