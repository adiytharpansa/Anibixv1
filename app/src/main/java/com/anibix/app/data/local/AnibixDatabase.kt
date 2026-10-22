package com.anibix.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedTitleEntity::class,
        WatchHistoryEntity::class,
        BookmarkEntity::class,
        DownloadQueueEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AnibixDatabase : RoomDatabase() {
    abstract fun cachedTitles(): CachedTitleDao
    abstract fun watchHistory(): WatchHistoryDao
    abstract fun bookmarks(): BookmarkDao
    abstract fun downloadQueue(): DownloadQueueDao
}
