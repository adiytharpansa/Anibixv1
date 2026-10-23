package com.anibix.app.torrent

import android.content.Context
import android.os.StatFs
import com.anibix.app.core.AppError
import com.anibix.app.core.AppResult
import com.anibix.app.data.settings.SettingsRepository
import com.anibix.app.data.settings.TorrentSettings
import com.frostwire.jlibtorrent.Priority
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.SettingsPack
import com.frostwire.jlibtorrent.TorrentFlags
import com.frostwire.jlibtorrent.TorrentHandle
import com.frostwire.jlibtorrent.TorrentInfo
import com.frostwire.jlibtorrent.swig.settings_pack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class TorrentState(
    val id: String,
    val title: String,
    val progress: Float,       // 0..1
    val downloadRate: Int,     // bytes/s
    val finished: Boolean,
    val file: File?
)

/** Info yang dibutuhkan HTTP server untuk menyajikan file yang sedang diunduh. */
class StreamSource(
    val handle: TorrentHandle,
    val info: TorrentInfo,
    val fileIndex: Int,
    val file: File
)

/**
 * Kontrak mesin torrent. Semua operasi berat berjalan di Dispatchers.IO dan
 * hasil yang bisa gagal dibungkus [AppResult] — UI tidak melihat exception.
 */
interface TorrentEngine {
    val states: StateFlow<List<TorrentState>>
    fun start()
    suspend fun startStream(id: String, title: String, magnet: String): AppResult<String>
    suspend fun startDownload(id: String, title: String, magnet: String): AppResult<Unit>
    fun pause(id: String)
    fun resume(id: String)
    fun remove(id: String, deleteFiles: Boolean = true)
    fun applySettings(settings: TorrentSettings)
    fun stop()
}

/**
 * Implementasi berbasis jlibtorrent (port Android dari libtorrent):
 * sequential download + piece deadline untuk putar instan, server HTTP lokal
 * untuk ExoPlayer, DHT/LSD/UPnP/NAT-PMP + batas bandwidth dari pengaturan.
 */
@Singleton
class LibTorrentEngine @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val settingsRepo: SettingsRepository,
) : TorrentEngine {
    private val session = SessionManager()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sources = ConcurrentHashMap<String, StreamSource>()
    private val titles = ConcurrentHashMap<String, String>()
    private var server: TorrentStreamServer? = null

    private val _states = MutableStateFlow<List<TorrentState>>(emptyList())
    override val states: StateFlow<List<TorrentState>> = _states.asStateFlow()

    private val downloadDir get() = File(ctx.getExternalFilesDir(null), "torrents").apply { mkdirs() }

    override fun start() {
        if (!session.isRunning) session.start()
        // DHT langsung dinyalakan di sini (bawaan aktif): jangan menunggu aliran
        // pengaturan — fetchMagnet yang keburu jalan tanpa DHT hanya bergantung
        // pada tracker dan jauh lebih mudah kehabisan waktu.
        runCatching { session.startDht() }
        // Terapkan pengaturan pengguna setiap kali berubah (DHT, throttle, dst.)
        scope.launch {
            settingsRepo.torrentSettings.collect { applySettings(it) }
        }
        scope.launch {
            while (isActive) {
                delay(1000)
                _states.value = sources.map { (id, src) ->
                    val st = src.handle.status()
                    TorrentState(
                        id = id,
                        title = titles[id] ?: id,
                        progress = st.progress(),
                        downloadRate = st.downloadPayloadRate(),
                        finished = st.isFinished,
                        file = src.file
                    )
                }
            }
        }
    }

    override fun applySettings(settings: TorrentSettings) {
        if (!session.isRunning) return
        runCatching {
            val sp = SettingsPack()
            sp.enableDht(settings.enableDht)
            sp.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), settings.enableLsd)
            sp.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), settings.enableUpnp)
            sp.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), settings.enableNatPmp)
            sp.downloadRateLimit(if (settings.downloadLimitKb > 0) settings.downloadLimitKb * 1024 else 0)
            sp.uploadRateLimit(if (settings.uploadLimitKb > 0) settings.uploadLimitKb * 1024 else 0)
            sp.connectionsLimit(settings.maxConnections)
            sp.activeDownloads(settings.maxActiveDownloads)
            session.applySettings(sp)
            if (settings.enableDht) runCatching { session.startDht() }
        }
    }

    private fun hasDiskSpace(minBytes: Long = 200L * 1024 * 1024): Boolean = runCatching {
        StatFs(downloadDir.absolutePath).availableBytes > minBytes
    }.getOrDefault(true)

    private suspend fun add(id: String, title: String, magnet: String, sequential: Boolean): AppResult<StreamSource> =
        withContext(Dispatchers.IO) {
            sources[id]?.let { existing ->
                if (sequential) runCatching { existing.handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD) }
                return@withContext AppResult.Success(existing)
            }
            if (!hasDiskSpace()) return@withContext AppResult.Failure(AppError.DiskFull())
            try {
                val data = fetchMetadata(augmentTrackers(magnet))
                    ?: return@withContext AppResult.Failure(AppError.Timeout("metadata torrent"))
                val ti = TorrentInfo.bdecode(data)

                session.download(ti, downloadDir, null, null, null)
                // download() berjalan async (async_add_torrent) dan tidak menerima
                // flags; tunggu handle muncul, lalu pasang SEQUENTIAL_DOWNLOAD.
                var handle: TorrentHandle? = null
                var tries = 0
                while (handle == null && tries < 40) {
                    handle = session.find(ti.infoHash())
                    if (handle == null) { Thread.sleep(250); tries++ }
                }
                val found = handle
                    ?: return@withContext AppResult.Failure(AppError.Torrent("Handle torrent tidak ditemukan"))
                if (sequential) found.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)

                // Pilih file video terbesar, abaikan sisanya
                val files = ti.files()
                val idx = (0 until files.numFiles()).maxByOrNull { files.fileSize(it) } ?: 0
                for (i in 0 until files.numFiles()) {
                    found.filePriority(i, if (i == idx) Priority.NORMAL else Priority.IGNORE)
                }
                // Panaskan awal berkas: beri tenggat berjenjang pada kepingan
                // pertama dan kepingan terakhir (info penutup), supaya data awal
                // mengalir lebih dulu dan pemutar tidak lama menggantung.
                runCatching {
                    val pieceLen = ti.pieceLength().toLong()
                    val firstPiece = (files.fileOffset(idx) / pieceLen).toInt()
                    val lastPiece = ((files.fileOffset(idx) + files.fileSize(idx) - 1) / pieceLen).toInt()
                    for (k in 0 until 12) {
                        val piece = firstPiece + k
                        if (piece <= lastPiece) found.setPieceDeadline(piece, k * 100)
                    }
                    found.setPieceDeadline(lastPiece, 0)
                }
                val src = StreamSource(found, ti, idx, File(downloadDir, files.filePath(idx)))
                sources[id] = src
                titles[id] = title
                AppResult.Success(src)
            } catch (e: Exception) {
                AppResult.Failure(AppError.Torrent(e.message))
            }
        }

    /**
     * Ambil metadata magnet dengan sabar: DHT yang baru mulai bisa butuh
     * 10-30 dtk hanya untuk bootstrap, jadi batas 30 dtk terlalu pendek.
     * Dua percobaan x 120 dtk; berhenti pada keberhasilan pertama.
     */
    /**
     * Tracker cadangan yang ditambahkan ke setiap magnet. Magnet katalog hanya
     * membawa tracker bawaan rilisnya (umumnya semua UDP); daftar ini menambah
     * tracker UDP keandalan tinggi plus jalur HTTP/HTTPS yang pengumumannya
     * berjalan lewat TCP, berguna di jaringan seluler yang menyulitkan UDP.
     */
    private val extraTrackers = listOf(
        "udp://tracker.openbittorrent.com:6969/announce",
        "udp://tracker.leechers-paradise.org:6969/announce",
        "udp://tracker.coppersurfer.tk:6969/announce",
        "udp://tracker.pirateparty.gr:6969/announce",
        "udp://tracker.internetwarriors.net:1337/announce",
        "udp://tracker.cyberia.is:6969/announce",
        "http://tracker.opentrackr.org:1337/announce",
        "https://tracker.opentrackr.org:1337/announce",
        "https://tracker.torrent.eu.org:451/announce",
    )

    private fun augmentTrackers(magnet: String): String {
        val suffix = extraTrackers.joinToString("") {
            "&tr=" + java.net.URLEncoder.encode(it, "UTF-8")
        }
        return magnet + suffix
    }

    private fun fetchMetadata(magnet: String): ByteArray? {
        repeat(2) {
            val d = runCatching { session.fetchMagnet(magnet, 120) }.getOrNull()
            if (d != null) return d
        }
        return null
    }

    override suspend fun startStream(id: String, title: String, magnet: String): AppResult<String> =
        when (val r = add(id, title, magnet, sequential = true)) {
            is AppResult.Failure -> r
            is AppResult.Success -> {
                val s = server ?: TorrentStreamServer(8888) { sources[it] }.also {
                    it.start(); server = it
                }
                AppResult.Success("http://127.0.0.1:${s.listeningPort}/stream/$id")
            }
        }

    override suspend fun startDownload(id: String, title: String, magnet: String): AppResult<Unit> =
        add(id, title, magnet, sequential = false).map { }

    override fun pause(id: String) { runCatching { sources[id]?.handle?.pause() } }
    override fun resume(id: String) { runCatching { sources[id]?.handle?.resume() } }

    override fun remove(id: String, deleteFiles: Boolean) {
        sources.remove(id)?.let { runCatching { session.remove(it.handle) } }
        if (deleteFiles) { /* TODO: hapus file di downloadDir */ }
        titles.remove(id)
    }

    override fun stop() { server?.stop(); session.stop(); scope.cancel() }
}
