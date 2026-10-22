package com.anibix.app

import android.app.Application
import com.anibix.app.data.CatalogRepository
import com.anibix.app.data.FakeCatalogRepository
import com.anibix.app.torrent.TorrentEngine

class FlixApp : Application() {
    // Ganti dengan DI (Hilt/Koin) kalau proyek sudah membesar
    val catalog: CatalogRepository by lazy { FakeCatalogRepository() }
    val engine: TorrentEngine by lazy { TorrentEngine(this) }

    override fun onCreate() {
        super.onCreate()
        engine.start()
    }
}
