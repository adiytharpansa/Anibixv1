package com.anibix.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Salinan katalog terakhir dari jaringan — fallback offline (network-first, cache fallback). */
@Entity(tableName = "cached_titles")
data class CachedTitleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val synopsis: String,
    val posterUrl: String,
    val backdropUrl: String,
    val magnet: String?,
    val year: Int,
    val genre: String,
    val rating: Double,
    val anilistId: Int?,
    val cachedAt: Long,
)

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val titleId: String,
    val titleName: String,
    val posterUrl: String,
    val episodeNumber: Int,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val titleId: String,
    val titleName: String,
    val posterUrl: String,
    val addedAt: Long,
)

/** Antrean unduhan persisten (bertahan sesudah app mati; engine melanjutkan dari folder torrent). */
@Entity(tableName = "download_queue")
data class DownloadQueueEntity(
    @PrimaryKey val titleId: String,
    val titleName: String,
    val magnet: String,
    val queuedAt: Long,
)
