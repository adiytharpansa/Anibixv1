package com.anibix.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anibix.app.core.AppResult
import com.anibix.app.data.CatalogRepository
import com.anibix.app.data.Category
import com.anibix.app.data.Episode
import com.anibix.app.data.Title
import com.anibix.app.data.local.BookmarkDao
import com.anibix.app.data.local.BookmarkEntity
import com.anibix.app.data.local.DownloadQueueDao
import com.anibix.app.data.local.DownloadQueueEntity
import com.anibix.app.data.local.WatchHistoryDao
import com.anibix.app.data.local.WatchHistoryEntity
import com.anibix.app.data.settings.PlayerSettings
import com.anibix.app.data.settings.SettingsRepository
import com.anibix.app.data.settings.TorrentSettings
import com.anibix.app.torrent.TorrentEngine
import com.anibix.app.torrent.TorrentState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Keadaan layar Beranda (UDF): Loading → Content / Error, selalu satu arah. */
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Error(val message: String) : HomeUiState
    data class Content(
        val categories: List<Category>,
        val continueWatching: List<WatchHistoryEntity>,
    ) : HomeUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repo: CatalogRepository,
    private val engine: TorrentEngine,
    private val historyDao: WatchHistoryDao,
    private val bookmarkDao: BookmarkDao,
    private val downloadQueueDao: DownloadQueueDao,
    val settingsRepo: SettingsRepository,
) : ViewModel() {

    private val _home = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val homeState: StateFlow<HomeUiState> = _home.asStateFlow()

    private val _titles = MutableStateFlow<List<Title>>(emptyList())
    val titles: StateFlow<List<Title>> = _titles.asStateFlow()

    val downloads: StateFlow<List<TorrentState>> = engine.states
    val downloadQueue: StateFlow<List<DownloadQueueEntity>> =
        downloadQueueDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val bookmarks: StateFlow<List<BookmarkEntity>> =
        bookmarkDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val continueWatching: StateFlow<List<WatchHistoryEntity>> =
        historyDao.observeRecent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val torrentSettings: StateFlow<TorrentSettings> =
        settingsRepo.torrentSettings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TorrentSettings())
    val playerSettings: StateFlow<PlayerSettings> =
        settingsRepo.playerSettings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerSettings())

    private val _streamUrl = MutableStateFlow<String?>(null)
    val streamUrl: StateFlow<String?> = _streamUrl.asStateFlow()
    private val _streamError = MutableStateFlow<String?>(null)
    val streamError: StateFlow<String?> = _streamError.asStateFlow()

    init {
        loadHome()
        resumeQueuedDownloads()
    }

    fun loadHome() {
        _home.value = HomeUiState.Loading
        viewModelScope.launch {
            runCatching { repo.categories() }
                .onSuccess { cats ->
                    _titles.value = cats.flatMap { it.titles }.distinctBy { it.id }
                    _home.value = if (cats.isEmpty()) {
                        HomeUiState.Error("Katalog kosong dan tidak ada cache. Periksa koneksi lalu coba lagi.")
                    } else {
                        HomeUiState.Content(cats, continueWatching.value)
                    }
                }
                .onFailure { _home.value = HomeUiState.Error(it.message ?: "Gagal memuat katalog") }
        }
    }

    suspend fun title(id: String): Title? = repo.title(id)

    fun isBookmarked(id: String): Flow<Boolean> = bookmarkDao.observeIsBookmarked(id)

    fun toggleBookmark(t: Title, currently: Boolean) {
        viewModelScope.launch {
            if (currently) bookmarkDao.remove(t.id)
            else bookmarkDao.add(BookmarkEntity(t.id, t.name, t.posterUrl, System.currentTimeMillis()))
        }
    }

    /** Minta URL stream untuk satu episode; hasilnya di [streamUrl]/[streamError]. */
    fun stream(t: Title, episode: Episode) {
        // Selesaikan episode ke entri daftar putar judul itu sendiri: pemanggil
        // bisa saja menyerahkan Episode polos (tanpa magnet) hasil susunan UI,
        // magnet per episode hanya ada di entri playlist judul.
        val resolved = t.playlist().firstOrNull { it.number == episode.number } ?: episode
        val magnet = t.magnetFor(resolved)
        if (magnet == null) {
            _streamUrl.value = null
            _streamError.value = "Judul ini hanya metadata — belum ada sumber torrent untuk episode ini."
            return
        }
        val streamId = if (t.playlist().size > 1) "${t.id}-ep${episode.number}" else t.id
        viewModelScope.launch {
            _streamUrl.value = null; _streamError.value = null
            when (val r = engine.startStream(streamId, t.name, magnet)) {
                is AppResult.Success -> _streamUrl.value = r.data
                is AppResult.Failure -> _streamError.value = r.error.userMessage
            }
        }
    }

    fun download(t: Title) {
        val magnet = t.magnet ?: return
        viewModelScope.launch {
            downloadQueueDao.add(DownloadQueueEntity(t.id, t.name, magnet, System.currentTimeMillis()))
            when (val r = engine.startDownload(t.id, t.name, magnet)) {
                is AppResult.Failure -> _streamError.value = r.error.userMessage
                is AppResult.Success -> Unit
            }
        }
    }

    private fun resumeQueuedDownloads() {
        viewModelScope.launch {
            runCatching { downloadQueueDao.all() }.getOrDefault(emptyList()).forEach { q ->
                engine.startDownload(q.titleId, q.titleName, q.magnet)
            }
        }
    }

    fun saveProgress(t: Title, episode: Episode, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        viewModelScope.launch {
            historyDao.upsert(
                WatchHistoryEntity(
                    titleId = t.id, titleName = t.name, posterUrl = t.posterUrl,
                    episodeNumber = episode.number, positionMs = positionMs,
                    durationMs = durationMs, updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    suspend fun savedPosition(titleId: String): Long =
        runCatching { historyDao.forTitle(titleId)?.positionMs ?: 0L }.getOrDefault(0L)

    fun pause(id: String) = engine.pause(id)
    fun resume(id: String) = engine.resume(id)
    fun remove(id: String) {
        engine.remove(id)
        viewModelScope.launch { downloadQueueDao.remove(id) }
    }

    fun setTorrentSettings(settings: TorrentSettings) {
        viewModelScope.launch { settingsRepo.setTorrent(settings) }
    }

    fun setPlayerSettings(settings: PlayerSettings) {
        viewModelScope.launch { settingsRepo.setPlayer(settings) }
    }
}
