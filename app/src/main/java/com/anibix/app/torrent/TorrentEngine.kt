package com.anibix.app.torrent

import android.content.Context
import com.frostwire.jlibtorrent.Priority
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.TorrentFlags
import com.frostwire.jlibtorrent.TorrentHandle
import com.frostwire.jlibtorrent.TorrentInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

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

class TorrentEngine(private val ctx: Context) {
    private val session = SessionManager()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sources = ConcurrentHashMap<String, StreamSource>()
    private val titles = ConcurrentHashMap<String, String>()
    private var server: TorrentStreamServer? = null

    private val _states = MutableStateFlow<List<TorrentState>>(emptyList())
    val states: StateFlow<List<TorrentState>> = _states.asStateFlow()

    private val downloadDir get() = File(ctx.getExternalFilesDir(null), "torrents").apply { mkdirs() }

    fun start() {
        if (!session.isRunning) session.start()
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

    /**
     * Tambah torrent dari magnet. [sequential]=true untuk streaming
     * (unduh berurutan dari awal file), false untuk download biasa.
     */
    suspend fun add(id: String, title: String, magnet: String, sequential: Boolean): StreamSource =
        withContext(Dispatchers.IO) {
            sources[id]?.let { existing ->
                if (sequential) existing.handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
                return@withContext existing
            }
            val data = session.fetchMagnet(magnet, 30)
                ?: error("Gagal mengambil metadata torrent (timeout)")
            val ti = TorrentInfo.bdecode(data)

            session.download(ti, downloadDir, null, null, null)
            // download() di jlibtorrent 1.2.0.18 berjalan async (async_add_torrent)
            // dan tidak menerima flags; tunggu handle muncul, lalu pasang
            // SEQUENTIAL_DOWNLOAD lewat handle.setFlags().
            var handle: TorrentHandle? = null
            var tries = 0
            while (handle == null && tries < 40) {
                handle = session.find(ti.infoHash())
                if (handle == null) { Thread.sleep(250); tries++ }
            }
            val found = handle ?: error("Handle torrent tidak ditemukan")
            if (sequential) found.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)

            // Pilih file video terbesar, abaikan sisanya
            val files = ti.files()
            val idx = (0 until files.numFiles()).maxByOrNull { files.fileSize(it) } ?: 0
            for (i in 0 until files.numFiles()) {
                found.filePriority(i, if (i == idx) Priority.NORMAL else Priority.IGNORE)
            }
            val src = StreamSource(found, ti, idx, File(downloadDir, files.filePath(idx)))
            sources[id] = src
            titles[id] = title
            src
        }

    /** Mulai streaming; kembalikan URL lokal untuk ExoPlayer. */
    suspend fun startStream(id: String, title: String, magnet: String): String {
        add(id, title, magnet, sequential = true)
        val s = server ?: TorrentStreamServer(8888) { sources[it] }.also {
            it.start(); server = it
        }
        return "http://127.0.0.1:${s.listeningPort}/stream/$id"
    }

    suspend fun startDownload(id: String, title: String, magnet: String) {
        add(id, title, magnet, sequential = false)
    }

    fun pause(id: String) = sources[id]?.handle?.pause()
    fun resume(id: String) = sources[id]?.handle?.resume()

    fun remove(id: String, deleteFiles: Boolean = true) {
        sources.remove(id)?.let { session.remove(it.handle) }
        if (deleteFiles) { /* TODO: hapus file di downloadDir */ }
        titles.remove(id)
    }

    fun stop() { server?.stop(); session.stop(); scope.cancel() }
}
