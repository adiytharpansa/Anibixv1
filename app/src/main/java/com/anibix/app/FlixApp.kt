package com.anibix.app

import android.app.Application
import com.anibix.app.torrent.TorrentEngine
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FlixApp : Application() {
    @Inject
    lateinit var engine: TorrentEngine

    override fun onCreate() {
        super.onCreate()
        engine.start()
    }
}
