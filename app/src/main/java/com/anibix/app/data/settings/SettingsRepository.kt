package com.anibix.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Pengaturan mesin torrent — diterapkan ke sesi libtorrent lewat SettingsPack. */
data class TorrentSettings(
    val enableDht: Boolean = true,
    val enableLsd: Boolean = true,          // Local Peer Discovery
    val enableUpnp: Boolean = true,         // NAT traversal (UPnP)
    val enableNatPmp: Boolean = true,       // NAT traversal (NAT-PMP)
    val downloadLimitKb: Int = 0,           // 0 = tanpa batas
    val uploadLimitKb: Int = 0,             // 0 = tanpa batas
    val maxConnections: Int = 200,
    val maxActiveDownloads: Int = 3,
)

/** Pengaturan pemutar: gaya subtitle + kecepatan bawaan. */
data class PlayerSettings(
    val subtitleTextSize: Float = 1.0f,     // pengali ukuran teks subtitle Media3
    val subtitleColorArgb: Int = 0xFFFFFFFF.toInt(),
    val subtitleBackgroundArgb: Int = 0x80000000.toInt(),
    val defaultSpeed: Float = 1.0f,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "anibix_settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val DHT = booleanPreferencesKey("tor_dht")
        val LSD = booleanPreferencesKey("tor_lsd")
        val UPNP = booleanPreferencesKey("tor_upnp")
        val NATPMP = booleanPreferencesKey("tor_natpmp")
        val DL = intPreferencesKey("tor_dl_kb")
        val UL = intPreferencesKey("tor_ul_kb")
        val CONN = intPreferencesKey("tor_conn")
        val ACTIVE = intPreferencesKey("tor_active")
        val SUB_SIZE = floatPreferencesKey("sub_size")
        val SUB_COLOR = intPreferencesKey("sub_color")
        val SUB_BG = intPreferencesKey("sub_bg")
        val SPEED = floatPreferencesKey("player_speed")
    }

    val torrentSettings: Flow<TorrentSettings> = context.dataStore.data.map { p ->
        TorrentSettings(
            enableDht = p[Keys.DHT] ?: true,
            enableLsd = p[Keys.LSD] ?: true,
            enableUpnp = p[Keys.UPNP] ?: true,
            enableNatPmp = p[Keys.NATPMP] ?: true,
            downloadLimitKb = p[Keys.DL] ?: 0,
            uploadLimitKb = p[Keys.UL] ?: 0,
            maxConnections = p[Keys.CONN] ?: 200,
            maxActiveDownloads = p[Keys.ACTIVE] ?: 3,
        )
    }

    val playerSettings: Flow<PlayerSettings> = context.dataStore.data.map { p ->
        PlayerSettings(
            subtitleTextSize = p[Keys.SUB_SIZE] ?: 1.0f,
            subtitleColorArgb = p[Keys.SUB_COLOR] ?: 0xFFFFFFFF.toInt(),
            subtitleBackgroundArgb = p[Keys.SUB_BG] ?: 0x80000000.toInt(),
            defaultSpeed = p[Keys.SPEED] ?: 1.0f,
        )
    }

    suspend fun setTorrent(settings: TorrentSettings) {
        context.dataStore.edit { p ->
            p[Keys.DHT] = settings.enableDht
            p[Keys.LSD] = settings.enableLsd
            p[Keys.UPNP] = settings.enableUpnp
            p[Keys.NATPMP] = settings.enableNatPmp
            p[Keys.DL] = settings.downloadLimitKb
            p[Keys.UL] = settings.uploadLimitKb
            p[Keys.CONN] = settings.maxConnections
            p[Keys.ACTIVE] = settings.maxActiveDownloads
        }
    }

    suspend fun setPlayer(settings: PlayerSettings) {
        context.dataStore.edit { p ->
            p[Keys.SUB_SIZE] = settings.subtitleTextSize
            p[Keys.SUB_COLOR] = settings.subtitleColorArgb
            p[Keys.SUB_BG] = settings.subtitleBackgroundArgb
            p[Keys.SPEED] = settings.defaultSpeed
        }
    }
}
