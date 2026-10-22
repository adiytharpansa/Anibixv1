package com.anibix.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.anibix.app.ui.MainViewModel
import com.anibix.app.ui.theme.FlixBlack
import com.anibix.app.ui.theme.FlixMuted
import com.anibix.app.ui.theme.FlixRed

/** Pengaturan: mesin torrent (DHT/LSD/NAT/bandwidth) + gaya subtitle & kecepatan bawaan. */
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val torrent by vm.torrentSettings.collectAsState()
    val player by vm.playerSettings.collectAsState()

    Column(
        Modifier.fillMaxSize().background(FlixBlack).statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
            }
            Text("Pengaturan", style = MaterialTheme.typography.headlineSmall)
        }

        SectionTitle("Torrent")
        SwitchRow("DHT (peer tanpa tracker)", torrent.enableDht) {
            vm.setTorrentSettings(torrent.copy(enableDht = it))
        }
        SwitchRow("Local Peer Discovery (LPD)", torrent.enableLsd) {
            vm.setTorrentSettings(torrent.copy(enableLsd = it))
        }
        SwitchRow("UPnP (NAT traversal)", torrent.enableUpnp) {
            vm.setTorrentSettings(torrent.copy(enableUpnp = it))
        }
        SwitchRow("NAT-PMP (NAT traversal)", torrent.enableNatPmp) {
            vm.setTorrentSettings(torrent.copy(enableNatPmp = it))
        }
        SliderRow(
            "Batas unduh", torrent.downloadLimitKb,
            valueText = if (torrent.downloadLimitKb == 0) "Tanpa batas" else "${torrent.downloadLimitKb} KB/s",
            range = 0f..10240f
        ) { vm.setTorrentSettings(torrent.copy(downloadLimitKb = it)) }
        SliderRow(
            "Batas unggah", torrent.uploadLimitKb,
            valueText = if (torrent.uploadLimitKb == 0) "Tanpa batas" else "${torrent.uploadLimitKb} KB/s",
            range = 0f..5120f
        ) { vm.setTorrentSettings(torrent.copy(uploadLimitKb = it)) }
        SliderRow(
            "Maks. koneksi", torrent.maxConnections,
            valueText = "${torrent.maxConnections}",
            range = 50f..500f
        ) { vm.setTorrentSettings(torrent.copy(maxConnections = it)) }
        SliderRow(
            "Unduhan aktif bersamaan", torrent.maxActiveDownloads,
            valueText = "${torrent.maxActiveDownloads}",
            range = 1f..5f
        ) { vm.setTorrentSettings(torrent.copy(maxActiveDownloads = it)) }

        SectionTitle("Pemutar & Subtitle")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Ukuran teks subtitle: ${"%.1f".format(player.subtitleTextSize)}x", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = player.subtitleTextSize,
                onValueChange = { vm.setPlayerSettings(player.copy(subtitleTextSize = (it * 10).toInt() / 10f)) },
                valueRange = 0.5f..2.0f, steps = 14,
                colors = SliderDefaults.colors(thumbColor = FlixRed, activeTrackColor = FlixRed)
            )
            Text("Warna teks subtitle", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val colors = listOf(
                    "Putih" to 0xFFFFFFFF.toInt(),
                    "Kuning" to 0xFFFFFF00.toInt(),
                    "Sian" to 0xFF00FFFF.toInt(),
                )
                colors.forEach { (label, argb) ->
                    FilterChip(
                        selected = player.subtitleColorArgb == argb,
                        onClick = { vm.setPlayerSettings(player.copy(subtitleColorArgb = argb)) },
                        label = { Text(label) }
                    )
                }
            }
            Text("Latar subtitle", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val bgs = listOf(
                    "Gelap" to 0x80000000.toInt(),
                    "Transparan" to 0x00000000,
                )
                bgs.forEach { (label, argb) ->
                    FilterChip(
                        selected = player.subtitleBackgroundArgb == argb,
                        onClick = { vm.setPlayerSettings(player.copy(subtitleBackgroundArgb = argb)) },
                        label = { Text(label) }
                    )
                }
            }
            Text("Kecepatan bawaan", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.75f, 1.0f, 1.25f, 1.5f).forEach { speed ->
                    FilterChip(
                        selected = player.defaultSpeed == speed,
                        onClick = { vm.setPlayerSettings(player.copy(defaultSpeed = speed)) },
                        label = { Text("${speed}x") }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Batas bandwidth membantu menghemat baterai dan kuota. " +
                    "Perubahan torrent berlaku langsung ke sesi yang berjalan.",
                style = MaterialTheme.typography.bodySmall, color = FlixMuted
            )
        }
        Spacer(Modifier.navigationBarsPadding().height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleMedium, color = FlixRed,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp)
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = FlixRed)
        )
    }
}

@Composable
private fun SliderRow(label: String, value: Int, valueText: String, range: ClosedFloatingPointRange<Float>, onChange: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text("$label: $valueText", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = FlixRed, activeTrackColor = FlixRed)
        )
    }
}
